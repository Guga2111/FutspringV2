package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.notification.NotificationService;
import com.futspring.backend.domain.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.futspring.backend.domain.notification.NotificationType.SUBSCRIPTION_ENDING;
import static com.futspring.backend.domain.notification.NotificationType.SUBSCRIPTION_PAST_DUE;

/**
 * Every day at 09:00 in São Paulo, tells the creator when the pelada's trial or paid period ends within
 * {@link #ENDING_NOTICE} (SUBSCRIPTION_ENDING) and when a paid period has entered its grace (SUBSCRIPTION_PAST_DUE).
 * Each notice stores the valid_until it was about and the queries skip a pelada already told about that validity,
 * so a repeated or late run never duplicates, and a renewal (a new valid_until) gets its own notice. Nothing changes
 * the subscription's state; each pelada runs in its own transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionNoticeJob {

    static final Duration ENDING_NOTICE = Duration.ofDays(5);

    private final PeladaSubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Sao_Paulo")
    public void run() {
        Instant now = Instant.now(clock);
        notifyEach(SUBSCRIPTION_ENDING, subscriptionRepository.findEndingWithoutNotice(now, now.plus(ENDING_NOTICE)));
        notifyEach(SUBSCRIPTION_PAST_DUE,
                subscriptionRepository.findPastDueWithoutNotice(now, now.minus(SubscriptionState.GRACE)));
    }

    // One transaction per pelada: a failing one rolls back only its own notice
    private void notifyEach(NotificationType type, List<PeladaSubscription> subscriptions) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        for (PeladaSubscription subscription : subscriptions) {
            try {
                transaction.executeWithoutResult(status -> notificationService.notifySubscription(type,
                        subscription.getPelada(), subscription.getValidUntil()));
            } catch (RuntimeException e) {
                log.error("{} failed for pelada {}", type, subscription.getPelada().getId(), e);
            }
        }
    }
}
