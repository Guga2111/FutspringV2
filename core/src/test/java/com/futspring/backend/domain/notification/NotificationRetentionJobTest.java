package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-10-28T04:00:00Z");

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void deletes_readOlderThan90Days_andAnyOlderThan180Days() {
        User user = userRepository.save(User.builder().email("ana@test.com").username("ana").password("hash").build());
        Notification readOld = save(user, 91, true);
        Notification readRecent = save(user, 89, true);
        Notification unreadOld = save(user, 179, false);
        Notification unreadExpired = save(user, 181, false);
        Notification readExpired = save(user, 200, true);

        new NotificationRetentionJob(notificationRepository, Clock.fixed(NOW, ZoneOffset.UTC)).deleteOldNotifications();

        assertThat(notificationRepository.findAll()).extracting(Notification::getId)
                .containsExactlyInAnyOrder(readRecent.getId(), unreadOld.getId())
                .doesNotContain(readOld.getId(), unreadExpired.getId(), readExpired.getId());
    }

    private Notification save(User recipient, int daysAgo, boolean read) {
        Instant createdAt = NOW.minus(Duration.ofDays(daysAgo));
        return notificationRepository.saveAndFlush(Notification.builder().recipient(recipient)
                .type(NotificationType.ADDED_TO_PELADA).createdAt(createdAt)
                .readAt(read ? createdAt.plusSeconds(60) : null).build());
    }
}
