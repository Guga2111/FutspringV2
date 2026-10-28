package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.notification.Notification;
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
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The scheduler against a real database with no test transaction around it, as it runs in production: each
 * pelada commits or rolls back on its own, and the listed (detached) peladas are reloaded so SESSION_CREATED can
 * read their members.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DailySchedulerTransactionTest {

    // Thu 29/10/2026 09:00 in São Paulo: Friday's session is created
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 30);

    @Autowired private PeladaRepository peladaRepository;
    @Autowired private DailyRepository dailyRepository;
    @Autowired private DailyAttendanceRepository attendanceRepository;
    @Autowired private DailyAttendanceBumpRepository bumpRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final NotificationDelivery delivery = mock(NotificationDelivery.class);

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        dailyRepository.deleteAll();
        peladaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void oneFailingPelada_rollsBackAlone_andTheOthersCommitWithTheirNotifications() {
        User ana = user("ana");
        User bia = user("bia");
        Pelada failing = pelada("Falha", ana, bia);
        Pelada ok = pelada("Certa", ana, bia);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        NotificationService notificationService = new NotificationService(notificationRepository,
                attendanceRepository, bumpRepository, delivery, clock);
        // The real listener, run in the publisher's transaction as Spring does; one pelada fails after its insert
        ApplicationEventPublisher publisher = event -> {
            DailyCreatedEvent created = (DailyCreatedEvent) event;
            if (created.daily().getPelada().getId().equals(failing.getId())) {
                throw new IllegalStateException("database down");
            }
            notificationService.onDailyCreated(created);
        };

        new DailySchedulerService(peladaRepository, dailyRepository, publisher, transactionManager, clock)
                .autoCreateDailies();

        List<Daily> dailies = dailyRepository.findAll();
        assertThat(dailies).singleElement().satisfies(d -> {
            assertThat(d.getPelada().getId()).isEqualTo(ok.getId());
            assertThat(d.getDailyDate()).isEqualTo(FRIDAY);
        });
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(2)
                .allMatch(n -> n.getType() == NotificationType.SESSION_CREATED)
                .allMatch(n -> n.getDaily().getId().equals(dailies.get(0).getId()));
        // delivered once, after the commit of the pelada that worked
        verify(delivery).deliver(anyList());
    }

    private User user(String name) {
        return userRepository.save(User.builder().email(name + "@test.com").username(name).password("hash").build());
    }

    private Pelada pelada(String name, User creator, User member) {
        Pelada pelada = Pelada.builder().name(name).dayOfWeek("FRIDAY").timeOfDay("20:00").duration(1f)
                .creator(creator).autoCreateDailyEnabled(true).build();
        pelada.getMembers().addAll(List.of(creator, member));
        pelada.getAdmins().add(creator);
        return peladaRepository.save(pelada);
    }
}
