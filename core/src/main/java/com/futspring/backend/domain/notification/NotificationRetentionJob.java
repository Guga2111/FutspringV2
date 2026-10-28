package com.futspring.backend.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Daily at 04:00 (server time): deletes read notifications older than 90 days and any older than 180 days
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetentionJob {

    static final Duration READ_RETENTION = Duration.ofDays(90);
    static final Duration RETENTION = Duration.ofDays(180);

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void deleteOldNotifications() {
        Instant now = Instant.now(clock);
        int read = notificationRepository.deleteReadCreatedBefore(now.minus(READ_RETENTION));
        int old = notificationRepository.deleteCreatedBefore(now.minus(RETENTION));
        log.info("Deleted {} read and {} expired notifications", read, old);
    }
}
