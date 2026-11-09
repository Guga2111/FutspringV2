package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    public static final Duration TRIAL_LENGTH = Duration.ofDays(30);

    /** Zone of the paid periods' "end of the day" and of the payment dates (the platform bills in Brazil). */
    public static final ZoneId BILLING_ZONE = ZoneId.of("America/Sao_Paulo");

    private final PeladaSubscriptionRepository subscriptionRepository;
    private final SubscriptionEventRepository eventRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Subscription of a pelada just created by {@code creator}: a 30-day trial when the account hasn't used its
     * one trial yet (marked now), otherwise PENDING (read-only until the platform admin records a payment).
     */
    @Transactional
    public PeladaSubscription start(Pelada pelada, User creator) {
        Instant now = Instant.now(clock);
        boolean trial = userRepository.markTrialUsed(creator.getId(), now) == 1;
        if (trial) {
            // keeps the managed entity in step with the row, so a later flush of the user doesn't write null back
            creator.setTrialUsedAt(now);
        }
        PeladaSubscription subscription = trial
                ? new PeladaSubscription(pelada, SubscriptionKind.TRIAL, now.plus(TRIAL_LENGTH), now)
                : new PeladaSubscription(pelada, SubscriptionKind.PENDING, null, now);
        subscriptionRepository.save(subscription);

        SubscriptionEvent event = new SubscriptionEvent();
        event.setPeladaId(pelada.getId());
        event.setActor(creator);
        event.setType(SubscriptionEventType.CREATED);
        event.setKindAfter(subscription.getKind());
        event.setValidUntilAfter(subscription.getValidUntil());
        event.setCreatedAt(now);
        eventRepository.save(event);
        return subscription;
    }

    /** Before a pelada is deleted: its subscription and history go, the payments stay (names are snapshots). */
    @Transactional
    public void deletePeladaData(Long peladaId) {
        eventRepository.deleteByPeladaId(peladaId);
        subscriptionRepository.deleteByPeladaId(peladaId);
        paymentRepository.detachPelada(peladaId);
    }

    // ---- Platform admin commands. The caller has checked the platform admin and holds the pelada's row lock ----

    /**
     * Records a payment: the subscription becomes PAID with the plan, until the end of the day (billing zone) one
     * month / year after {@code max(now, validUntil)}, so paying early never loses days; a pending Pix claim is
     * cleared. The payer is the pelada's creator (snapshot names).
     */
    @Transactional
    public SubscriptionPayment recordPayment(PeladaSubscription subscription, User actor, SubscriptionPlan plan,
                                             BigDecimal amount, LocalDate paidOn, String note) {
        Instant now = Instant.now(clock);
        Pelada pelada = subscription.getPelada();
        Change change = new Change(subscription);
        Instant start = runsUntilAfter(subscription, now) ? subscription.getValidUntil() : now;
        LocalDate startDay = start.atZone(BILLING_ZONE).toLocalDate();
        Instant end = endOfDay(plan == SubscriptionPlan.ANNUAL ? startDay.plusYears(1) : startDay.plusMonths(1));

        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setPeladaId(pelada.getId());
        payment.setPeladaName(pelada.getName());
        payment.setPayer(pelada.getCreator());
        payment.setPayerName(pelada.getCreator().getUsername());
        payment.setAmount(amount);
        payment.setPaidOn(paidOn);
        payment.setPlan(plan);
        payment.setPeriodStart(start);
        payment.setPeriodEnd(end);
        payment.setRecordedBy(actor);
        payment.setNote(note);
        payment.setCreatedAt(now);
        paymentRepository.save(payment);

        subscription.setKind(SubscriptionKind.PAID);
        subscription.setValidUntil(end);
        subscription.setPlan(plan);
        clearPixClaim(subscription);
        change.record(SubscriptionEventType.PAYMENT_RECORDED, actor, note, now);
        return payment;
    }

    /** PAID until the end of {@code day} (billing zone), in the past included: the admin may lock a pelada. */
    @Transactional
    public void setValidUntil(PeladaSubscription subscription, User actor, LocalDate day, String note) {
        Change change = new Change(subscription);
        subscription.setKind(SubscriptionKind.PAID);
        subscription.setValidUntil(endOfDay(day));
        change.record(SubscriptionEventType.VALID_UNTIL_SET, actor, note, Instant.now(clock));
    }

    /** {@code days} more from {@code max(now, validUntil)}, keeping the kind (lengthens a trial too). */
    @Transactional
    public void extendDays(PeladaSubscription subscription, User actor, int days, String note) {
        if (subscription.getKind() != SubscriptionKind.TRIAL && subscription.getKind() != SubscriptionKind.PAID) {
            throw new AppException(HttpStatus.CONFLICT, "Só dá para estender um teste ou uma assinatura paga");
        }
        Instant now = Instant.now(clock);
        Change change = new Change(subscription);
        Instant start = runsUntilAfter(subscription, now) ? subscription.getValidUntil() : now;
        subscription.setValidUntil(start.plus(Duration.ofDays(days)));
        change.record(SubscriptionEventType.DAYS_EXTENDED, actor, note, now);
    }

    /** EXEMPT or CANCELED: no end date any more (the event keeps the previous one). Exempting answers a Pix claim. */
    @Transactional
    public void exempt(PeladaSubscription subscription, User actor, String note) {
        Change change = new Change(subscription);
        subscription.setKind(SubscriptionKind.EXEMPT);
        subscription.setValidUntil(null);
        clearPixClaim(subscription);
        change.record(SubscriptionEventType.EXEMPTED, actor, note, Instant.now(clock));
    }

    @Transactional
    public void cancel(PeladaSubscription subscription, User actor, String note) {
        Change change = new Change(subscription);
        subscription.setKind(SubscriptionKind.CANCELED);
        subscription.setValidUntil(null);
        change.record(SubscriptionEventType.CANCELED, actor, note, Instant.now(clock));
    }

    @Transactional
    public void setFounder(PeladaSubscription subscription, User actor, boolean founder, String note) {
        Change change = new Change(subscription);
        subscription.setFounder(founder);
        change.record(SubscriptionEventType.FOUNDER_CHANGED, actor, note, Instant.now(clock));
    }

    /**
     * Voids a payment (the caller holds its row lock): it leaves the totals, the validity is untouched (the admin
     * adjusts it apart). Recorded in the pelada's history while the pelada exists.
     */
    @Transactional
    public void voidPayment(SubscriptionPayment payment, User actor, String reason) {
        if (payment.isVoided()) {
            throw new AppException(HttpStatus.CONFLICT, "Este pagamento já foi estornado");
        }
        Instant now = Instant.now(clock);
        payment.setVoidedAt(now);
        payment.setVoidReason(reason);
        if (payment.getPeladaId() == null) return;
        SubscriptionEvent event = new SubscriptionEvent();
        event.setPeladaId(payment.getPeladaId());
        event.setActor(actor);
        event.setType(SubscriptionEventType.PAYMENT_VOIDED);
        subscriptionRepository.findByPeladaId(payment.getPeladaId()).ifPresent(s -> {
            event.setKindBefore(s.getKind());
            event.setKindAfter(s.getKind());
            event.setValidUntilBefore(s.getValidUntil());
            event.setValidUntilAfter(s.getValidUntil());
        });
        event.setNote(reason);
        event.setCreatedAt(now);
        eventRepository.save(event);
    }

    /** The last second of {@code day} in the billing zone (not the next midnight, which would show the next day). */
    public static Instant endOfDay(LocalDate day) {
        return day.atTime(23, 59, 59).atZone(BILLING_ZONE).toInstant();
    }

    // A trial or paid period still running: a payment or an extension starts where it ends
    private static boolean runsUntilAfter(PeladaSubscription subscription, Instant now) {
        return (subscription.getKind() == SubscriptionKind.TRIAL || subscription.getKind() == SubscriptionKind.PAID)
                && subscription.getValidUntil() != null && subscription.getValidUntil().isAfter(now);
    }

    private static void clearPixClaim(PeladaSubscription subscription) {
        subscription.setPixClaimedAt(null);
        subscription.setClaimedPlan(null);
    }

    // The state before a command, written with the state after it as one event
    private final class Change {
        private final PeladaSubscription subscription;
        private final SubscriptionKind kindBefore;
        private final Instant validUntilBefore;

        Change(PeladaSubscription subscription) {
            this.subscription = subscription;
            this.kindBefore = subscription.getKind();
            this.validUntilBefore = subscription.getValidUntil();
        }

        void record(SubscriptionEventType type, User actor, String note, Instant now) {
            subscription.setUpdatedAt(now);
            SubscriptionEvent event = new SubscriptionEvent();
            event.setPeladaId(subscription.getPelada().getId());
            event.setActor(actor);
            event.setType(type);
            event.setKindBefore(kindBefore);
            event.setKindAfter(subscription.getKind());
            event.setValidUntilBefore(validUntilBefore);
            event.setValidUntilAfter(subscription.getValidUntil());
            event.setNote(note);
            event.setCreatedAt(now);
            eventRepository.save(event);
        }
    }
}
