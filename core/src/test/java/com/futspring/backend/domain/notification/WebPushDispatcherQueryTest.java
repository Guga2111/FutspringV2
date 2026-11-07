package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.support.QueryCounter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// The Web Push dispatch sends the same number of statements for 2 and 20 recipients (real repositories, H2).
// statements, not reads: reads counts every hydrated row, and the dispatch loads one subscription per device
class WebPushDispatcherQueryTest extends BaseIntegrationTest {

    private static final AtomicInteger USERS = new AtomicInteger();

    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private DailyRepository dailyRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PushSubscriptionRepository subscriptionRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private NotificationPreferenceService preferenceService;
    @Autowired private QueryCounter queryCounter;
    @Autowired private EntityManager entityManager;

    private WebPushDispatcherTest.FakeSender sender;
    private WebPushDispatcher dispatcher;
    private User admin;
    private Pelada pelada;
    private Daily daily;

    @BeforeEach
    void setUp() {
        sender = new WebPushDispatcherTest.FakeSender();
        // The test transaction is already open: the dispatcher's own transactions would only join it
        dispatcher = new WebPushDispatcher(notificationRepository, subscriptionRepository, preferenceService, sender,
                objectMapper, Clock.systemUTC(), TransactionOperations.withoutTransaction(),
                TransactionOperations.withoutTransaction(), new SimpleMeterRegistry());
        admin = user();
        pelada = peladaRepository.save(Pelada.builder().name("Pelada do Push").dayOfWeek("SATURDAY").timeOfDay("20:00")
                .duration(2f).creator(admin).members(new HashSet<>(Set.of(admin))).admins(new HashSet<>(Set.of(admin)))
                .numberOfTeams(2).playersPerTeam(5).build());
        daily = dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().plusDays(7))
                .dailyTime("20:00").build());
    }

    @Test
    void sendsAConstantNumberOfStatements() {
        statements(2); // warm-up
        assertThat(statements(20)).isEqualTo(statements(2));
    }

    @Test
    void sendsToEveryDeviceOfEveryRecipient() {
        List<Long> ids = batch(3);
        entityManager.clear();

        dispatcher.send(ids);

        assertThat(sender.sent).hasSize(6); // two devices each
        entityManager.clear(); // the bulk update doesn't touch the loaded entities
        assertThat(subscriptionRepository.findAll()).allSatisfy(s -> assertThat(s.getLastSuccessAt()).isNotNull());
    }

    private long statements(int recipients) {
        List<Long> ids = batch(recipients);
        entityManager.clear();
        int before = sender.sent.size();
        long statements = queryCounter.statements(() -> dispatcher.send(ids));
        assertThat(sender.sent.size() - before).isEqualTo(recipients * 2);
        return statements;
    }

    // One notification per recipient, two devices each, and a stored preference (e-mail only) for half of them
    private List<Long> batch(int recipients) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < recipients; i++) {
            User recipient = user();
            for (int d = 0; d < 2; d++) {
                subscriptionRepository.save(new PushSubscription(recipient,
                        "https://fcm.googleapis.com/fcm/send/" + recipient.getId() + "-" + d,
                        PushTestKeys.p256dh(), PushTestKeys.auth(), Instant.now()));
            }
            if (i % 2 == 0) {
                preferenceRepository.save(new NotificationPreference(recipient, NotificationCategory.ATTENDANCE, false, null));
            }
            ids.add(notificationRepository.save(Notification.builder().recipient(recipient)
                    .type(NotificationType.SESSION_CANCELED).pelada(pelada).daily(daily).actor(admin)
                    .createdAt(Instant.now()).build()).getId());
        }
        entityManager.flush();
        return ids;
    }

    private User user() {
        int n = USERS.incrementAndGet();
        return userRepository.save(User.builder().email("wp" + n + "@test.com").username("wp" + n).password("hash").build());
    }
}
