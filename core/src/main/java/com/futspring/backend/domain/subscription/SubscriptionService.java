package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    public static final Duration TRIAL_LENGTH = Duration.ofDays(30);

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
}
