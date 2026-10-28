package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaMensalista;
import com.futspring.backend.domain.pelada.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The scheduled job's recipient queries: the H2 schema has no uq_notifications_scheduled, so NOT EXISTS is the guard
@DataJpaTest
class NotificationRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-29T23:00:00Z");

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private PeladaMensalistaRepository mensalistaRepository;
    @Autowired private DailyRepository dailyRepository;
    @Autowired private DailyAttendanceRepository attendanceRepository;

    private User creator;
    private User admin;
    private User mensalista;
    private User diarista;
    private User confirmedMensalista;
    private User confirmedDiarista;
    private Pelada pelada;
    private Daily daily;

    @BeforeEach
    void setUp() {
        creator = user("creator");
        admin = user("admin");
        mensalista = user("mensalista");
        diarista = user("diarista");
        confirmedMensalista = user("confirmedmensalista");
        confirmedDiarista = user("confirmeddiarista");
        pelada = Pelada.builder().name("Pelada do Fut").dayOfWeek("FRIDAY").timeOfDay("20:00").duration(1f)
                .creator(creator).build();
        pelada.getMembers().addAll(List.of(creator, admin, mensalista, diarista, confirmedMensalista,
                confirmedDiarista));
        pelada.getAdmins().addAll(List.of(creator, admin));
        pelada = peladaRepository.save(pelada);
        mensalistaRepository.save(PeladaMensalista.builder().pelada(pelada).user(mensalista).build());
        mensalistaRepository.save(PeladaMensalista.builder().pelada(pelada).user(confirmedMensalista).build());
        daily = daily();
        attendanceRepository.save(new DailyAttendance(daily, confirmedMensalista, NOW));
        attendanceRepository.save(new DailyAttendance(daily, confirmedDiarista, NOW));
    }

    @Test
    void reminderRecipients_areTheMensalistasWhoHaventConfirmed() {
        assertThat(notificationRepository.findReminderRecipients(daily)).containsExactly(mensalista);
    }

    @Test
    void windowOpenRecipients_areTheOtherMembersWhoHaventConfirmed_creatorAndAdminsIncluded() {
        assertThat(notificationRepository.findWindowOpenRecipients(daily))
                .containsExactlyInAnyOrder(creator, admin, diarista);
    }

    @Test
    void recipients_alreadyNotifiedForThatSessionAndType_areLeftOut() {
        notify(mensalista, NotificationType.SESSION_REMINDER, daily);
        notify(diarista, NotificationType.DIARISTA_WINDOW_OPEN, daily);
        // another type doesn't count
        notify(admin, NotificationType.SESSION_CREATED, daily);

        assertThat(notificationRepository.findReminderRecipients(daily)).isEmpty();
        assertThat(notificationRepository.findWindowOpenRecipients(daily)).containsExactlyInAnyOrder(creator, admin);
    }

    @Test
    void recipients_notifiedForAnotherSession_stillCount() {
        Daily nextWeek = daily();
        notify(mensalista, NotificationType.SESSION_REMINDER, nextWeek);
        notify(diarista, NotificationType.DIARISTA_WINDOW_OPEN, nextWeek);

        assertThat(notificationRepository.findReminderRecipients(daily)).containsExactly(mensalista);
        assertThat(notificationRepository.findWindowOpenRecipients(daily)).contains(diarista);
    }

    private User user(String name) {
        return userRepository.save(User.builder().email(name + "@test.com").username(name).password("hash").build());
    }

    private Daily daily() {
        return dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.of(2026, 10, 30))
                .dailyTime("20:00").build());
    }

    private void notify(User recipient, NotificationType type, Daily session) {
        notificationRepository.save(Notification.builder().recipient(recipient).type(type).pelada(pelada)
                .daily(session).createdAt(NOW).build());
    }
}
