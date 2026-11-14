package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.daily.attendance.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceRepository;
import com.futspring.backend.domain.notification.NotificationDelivery;
import com.futspring.backend.domain.notification.NotificationRepository;
import com.futspring.backend.domain.notification.NotificationService;
import com.futspring.backend.domain.notification.NotificationType;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import org.assertj.core.groups.Tuple;

import static com.futspring.backend.domain.notification.NotificationType.SUBSCRIPTION_ENDING;
import static com.futspring.backend.domain.notification.NotificationType.SUBSCRIPTION_PAST_DUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The job against a real database with no test transaction around it, as it runs in production (each pelada
 * commits on its own). H2 has no uq_notifications_subscription, so a second run only stays quiet because of the
 * queries' NOT EXISTS.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SubscriptionNoticeJobTest {

    // Wed 04/11/2026 09:00 in São Paulo
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final Duration DAY = Duration.ofDays(1);

    @Autowired private PeladaSubscriptionRepository subscriptionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private DailyAttendanceRepository attendanceRepository;
    @Autowired private DailyAttendanceBumpRepository bumpRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final NotificationDelivery delivery = mock(NotificationDelivery.class);

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        subscriptionRepository.deleteAll();
        peladaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ending_withinFiveDays_tellsTheCreator_ofATrialOrAPaidPeriod_atTheExactEdge() {
        User ana = user("ana");
        Pelada trialAtEdge = subscribed("TrialBorda", ana, SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(5)));
        Pelada paid = subscribed("Paga", ana, SubscriptionKind.PAID, NOW.plus(DAY));
        subscribed("Longe", ana, SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(5)).plusSeconds(1));
        subscribed("Vencida", ana, SubscriptionKind.TRIAL, NOW);
        subscribed("Isenta", ana, SubscriptionKind.EXEMPT, null);
        subscribed("Pendente", ana, SubscriptionKind.PENDING, null);
        subscribed("Cancelada", ana, SubscriptionKind.CANCELED, NOW.plus(DAY));

        run(NOW);

        assertThat(rows()).containsExactlyInAnyOrder(
                tuple(SUBSCRIPTION_ENDING, trialAtEdge.getId(), ana.getId(), NOW.plus(Duration.ofDays(5))),
                tuple(SUBSCRIPTION_ENDING, paid.getId(), ana.getId(), NOW.plus(DAY)));
        assertThat(notificationRepository.findAll()).allMatch(n -> n.getActor() == null && n.getDaily() == null);
        // delivered after each pelada's commit
        verify(delivery, times(2)).deliver(anyList());
    }

    @Test
    void pastDue_onlyWhileInTheGrace_ofAPaidPeriod() {
        User ana = user("ana");
        Pelada justExpired = subscribed("Venceu", ana, SubscriptionKind.PAID, NOW);
        Pelada lastGraceSecond = subscribed("Fim", ana, SubscriptionKind.PAID, NOW.minus(Duration.ofDays(7)).plusSeconds(1));
        subscribed("Travada", ana, SubscriptionKind.PAID, NOW.minus(Duration.ofDays(7)));
        subscribed("TrialVencido", ana, SubscriptionKind.TRIAL, NOW.minus(DAY));

        run(NOW);

        assertThat(rows()).containsExactlyInAnyOrder(
                tuple(SUBSCRIPTION_PAST_DUE, justExpired.getId(), ana.getId(), NOW),
                tuple(SUBSCRIPTION_PAST_DUE, lastGraceSecond.getId(), ana.getId(),
                        NOW.minus(Duration.ofDays(7)).plusSeconds(1)));
    }

    @Test
    void runningAgain_orTheNextDays_createsNothingNew_untilTheValidityChanges() {
        User ana = user("ana");
        Pelada pelada = subscribed("Paga", ana, SubscriptionKind.PAID, NOW.plus(Duration.ofDays(3)));

        run(NOW);
        run(NOW);
        run(NOW.plus(DAY));
        assertThat(rows()).hasSize(1);

        // entering the grace is a notice of its own
        run(NOW.plus(Duration.ofDays(4)));
        run(NOW.plus(Duration.ofDays(5)));
        assertThat(rows()).extracting(t -> t.toList().get(0)).containsExactlyInAnyOrder(SUBSCRIPTION_ENDING,
                SUBSCRIPTION_PAST_DUE);

        // renewed: the new validity gets its own notice when it comes near
        PeladaSubscription subscription = subscriptionRepository.findByPeladaId(pelada.getId()).orElseThrow();
        Instant renewed = NOW.plus(Duration.ofDays(33));
        subscription.setValidUntil(renewed);
        subscriptionRepository.save(subscription);
        run(NOW.plus(Duration.ofDays(29)));
        assertThat(rows()).hasSize(3)
                .contains(tuple(SUBSCRIPTION_ENDING, pelada.getId(), ana.getId(), renewed));
    }

    @Test
    void goesToTheCurrentCreator_afterATransfer() {
        User ana = user("ana");
        User bia = user("bia");
        Pelada pelada = subscribed("Transferida", ana, SubscriptionKind.TRIAL, NOW.plus(DAY));
        pelada.setCreator(bia);
        peladaRepository.save(pelada);

        run(NOW);

        assertThat(rows()).containsExactly(tuple(SUBSCRIPTION_ENDING, pelada.getId(), bia.getId(), NOW.plus(DAY)));
    }

    @Test
    void oneFailingPelada_rollsBackAlone_andTheOthersAreNotified() {
        User ana = user("ana");
        Pelada failing = subscribed("Falha", ana, SubscriptionKind.TRIAL, NOW.plus(DAY));
        Pelada ok = subscribed("Certa", ana, SubscriptionKind.TRIAL, NOW.plus(DAY));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        // the real service; the failing pelada throws after its insert, inside its transaction
        NotificationService notificationService = new NotificationService(notificationRepository,
                attendanceRepository, bumpRepository, userRepository, delivery, clock) {
            @Override
            public void notifySubscription(NotificationType type, Pelada pelada, Instant validUntil) {
                super.notifySubscription(type, pelada, validUntil);
                if (pelada.getId().equals(failing.getId())) {
                    throw new IllegalStateException("database down");
                }
            }
        };

        new SubscriptionNoticeJob(subscriptionRepository, notificationService, transactionManager, clock).run();

        assertThat(rows()).containsExactly(tuple(SUBSCRIPTION_ENDING, ok.getId(), ana.getId(), NOW.plus(DAY)));
        verify(delivery).deliver(anyList());
    }

    private void run(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        NotificationService notificationService = new NotificationService(notificationRepository,
                attendanceRepository, bumpRepository, userRepository, delivery, clock);
        new SubscriptionNoticeJob(subscriptionRepository, notificationService, transactionManager, clock).run();
    }

    // (type, pelada, recipient, validUntil)
    private List<Tuple> rows() {
        return inTransaction(() -> notificationRepository.findAll().stream()
                .map(n -> tuple(n.getType(), n.getPelada().getId(), n.getRecipient().getId(), n.getValidUntil()))
                .toList());
    }

    private <T> T inTransaction(Supplier<T> body) {
        return new TransactionTemplate(transactionManager).execute(status -> body.get());
    }

    private User user(String name) {
        return userRepository.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH).email(name + "@test.com").username(name).password("hash").build());
    }

    private Pelada subscribed(String name, User creator, SubscriptionKind kind, Instant validUntil) {
        Pelada pelada = Pelada.builder().name(name).dayOfWeek("FRIDAY").timeOfDay("20:00").duration(1f)
                .creator(creator).build();
        pelada.getMembers().add(creator);
        pelada.getAdmins().add(creator);
        pelada = peladaRepository.save(pelada);
        subscriptionRepository.save(new PeladaSubscription(pelada, kind, validUntil, NOW.minus(Duration.ofDays(30))));
        return pelada;
    }
}
