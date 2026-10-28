package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaMensalista;
import com.futspring.backend.domain.pelada.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static com.futspring.backend.domain.notification.NotificationType.DIARISTA_WINDOW_OPEN;
import static com.futspring.backend.domain.notification.NotificationType.SESSION_REMINDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The job against a real H2 schema, which has no uq_notifications_scheduled: running it twice only stays
 * idempotent because the recipient queries skip whoever was already notified.
 */
@DataJpaTest
class ScheduledNotificationJobTest {

    // Fri 30/10/2026 20:00 in São Paulo (UTC-3): starts at 23:00Z, the diarista window opens 24 h before
    private static final LocalDate DATE = LocalDate.of(2026, 10, 30);
    private static final Instant OPENS_AT = Instant.parse("2026-10-29T23:00:00Z");
    private static final Instant STARTS_AT = Instant.parse("2026-10-30T23:00:00Z");

    @Autowired private DailyRepository dailyRepository;
    @Autowired private DailyAttendanceRepository attendanceRepository;
    @Autowired private DailyAttendanceBumpRepository bumpRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private PeladaMensalistaRepository mensalistaRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    private final NotificationDelivery delivery = mock(NotificationDelivery.class);

    private User creator;
    private User mensalista;
    private User diarista;
    private User confirmed;

    @BeforeEach
    void setUp() {
        creator = user("creator");
        mensalista = user("mensalista");
        diarista = user("diarista");
        confirmed = user("confirmed");
    }

    @Test
    void windowOpen_remindsMensalistas_andTellsDiaristas_butNotWhoConfirmed() {
        Pelada pelada = pelada("America/Sao_Paulo", 5, mensalista);
        Daily daily = daily(pelada, DATE, DailyStatus.SCHEDULED);

        run(OPENS_AT);

        assertThat(rows()).containsExactlyInAnyOrder(
                tuple(mensalista.getId(), SESSION_REMINDER, daily.getId()),
                tuple(creator.getId(), DIARISTA_WINDOW_OPEN, daily.getId()),
                tuple(diarista.getId(), DIARISTA_WINDOW_OPEN, daily.getId()));
        assertThat(notificationRepository.findAll()).allSatisfy(n -> {
            assertThat(n.getActor()).isNull();
            assertThat(n.getCreatedAt()).isEqualTo(OPENS_AT);
        });
        // the test transaction never commits, so nothing is delivered
        verifyNoInteractions(delivery);
    }

    @Test
    void beforeTheWindow_orOnceTheSessionStarted_notifiesNobody() {
        daily(pelada("America/Sao_Paulo", 5, mensalista), DATE, DailyStatus.SCHEDULED);

        run(OPENS_AT.minusSeconds(60));
        run(STARTS_AT);

        assertThat(rows()).isEmpty();
    }

    @Test
    void runningAgain_createsNothingNew_untilSomeoneNewIsDue() {
        Pelada pelada = pelada("America/Sao_Paulo", 5, mensalista);
        Daily daily = daily(pelada, DATE, DailyStatus.CONFIRMED);

        run(OPENS_AT);
        run(OPENS_AT.plusSeconds(15 * 60));
        assertThat(rows()).hasSize(3);

        User newcomer = user("newcomer");
        Pelada reloaded = peladaRepository.findById(pelada.getId()).orElseThrow();
        reloaded.getMembers().add(newcomer);
        peladaRepository.saveAndFlush(reloaded);
        run(OPENS_AT.plusSeconds(30 * 60));

        assertThat(rows()).hasSize(4).contains(tuple(newcomer.getId(), DIARISTA_WINDOW_OPEN, daily.getId()));
    }

    @Test
    void fullSession_onlyRemindsMensalistas() {
        // capacity 2, both spots taken
        Pelada pelada = pelada("America/Sao_Paulo", 1, mensalista);
        Daily daily = daily(pelada, DATE, DailyStatus.SCHEDULED);
        User other = user("other");
        Pelada reloaded = peladaRepository.findById(pelada.getId()).orElseThrow();
        reloaded.getMembers().add(other);
        peladaRepository.saveAndFlush(reloaded);
        attendanceRepository.saveAndFlush(new DailyAttendance(dailyRepository.findById(daily.getId()).orElseThrow(),
                other, OPENS_AT));

        run(OPENS_AT);

        assertThat(rows()).containsExactly(tuple(mensalista.getId(), SESSION_REMINDER, daily.getId()));
    }

    @Test
    void lockedOrCanceledSessions_areSkipped() {
        Pelada pelada = pelada("America/Sao_Paulo", 5, mensalista);
        daily(pelada, DATE, DailyStatus.IN_COURSE);
        daily(pelada, DATE, DailyStatus.FINISHED);
        daily(pelada, DATE, DailyStatus.CANCELED);

        run(OPENS_AT);

        assertThat(rows()).isEmpty();
    }

    @Test
    void sessionCreatedAfterItsWindowOpened_isSkipped() {
        Daily daily = daily(pelada("America/Sao_Paulo", 5, mensalista), DATE, DailyStatus.SCHEDULED);
        setCreatedAt(daily, OPENS_AT.plusSeconds(60));

        run(OPENS_AT.plusSeconds(15 * 60));

        assertThat(rows()).isEmpty();
    }

    @Test
    void window_followsThePeladasZone_acrossTheCanadianDstChange() {
        // Daylight time ends in Toronto on 2026-11-01; that evening's 20:00 is 01:00Z on the 2nd (UTC-5)
        Daily daily = daily(pelada("America/Toronto", 5, mensalista), LocalDate.of(2026, 11, 1), DailyStatus.SCHEDULED);
        Instant opensAt = Instant.parse("2026-11-01T01:00:00Z");

        run(opensAt.minusSeconds(60));
        assertThat(rows()).isEmpty();

        run(opensAt);
        assertThat(rows()).contains(tuple(mensalista.getId(), SESSION_REMINDER, daily.getId()));
    }

    @Test
    void oneFailingPelada_doesNotStopTheOthers() {
        daily(pelada("Not/AZone", 5, mensalista), DATE, DailyStatus.SCHEDULED);
        Daily ok = daily(pelada("America/Sao_Paulo", 5, mensalista), DATE, DailyStatus.SCHEDULED);

        run(OPENS_AT);

        assertThat(rows()).isNotEmpty().allMatch(row -> ok.getId().equals(row.toList().get(2)));
    }

    private void run(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        NotificationService notificationService = new NotificationService(notificationRepository,
                attendanceRepository, bumpRepository, delivery, clock);
        new ScheduledNotificationJob(dailyRepository, attendanceRepository, notificationRepository,
                notificationService, transactionManager, clock).run();
        entityManager.flush();
    }

    private List<Tuple> rows() {
        return notificationRepository.findAll().stream()
                .map(n -> tuple(n.getRecipient().getId(), n.getType(), n.getDaily().getId()))
                .toList();
    }

    private User user(String name) {
        return userRepository.save(User.builder().email(name + "@test.com").username(name).password("hash").build());
    }

    // 2 teams of `playersPerTeam`; members: the creator, `mensalistaUser` (marked), the diarista and `confirmed`
    private Pelada pelada(String timeZone, int playersPerTeam, User mensalistaUser) {
        Pelada pelada = Pelada.builder().name("Pelada " + timeZone).dayOfWeek("FRIDAY").timeOfDay("20:00")
                .duration(1f).creator(creator).timeZone(timeZone).numberOfTeams(2).playersPerTeam(playersPerTeam)
                .build();
        pelada.getMembers().addAll(List.of(creator, mensalistaUser, diarista, confirmed));
        pelada.getAdmins().add(creator);
        pelada = peladaRepository.save(pelada);
        mensalistaRepository.save(PeladaMensalista.builder().pelada(pelada).user(mensalistaUser).build());
        return pelada;
    }

    // `confirmed` is confirmed in every session; created well before its window opened
    private Daily daily(Pelada pelada, LocalDate date, DailyStatus status) {
        Daily daily = dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(date).dailyTime("20:00")
                .status(status).build());
        attendanceRepository.save(new DailyAttendance(daily, confirmed, OPENS_AT));
        setCreatedAt(daily, Instant.parse("2026-10-01T12:00:00Z"));
        return daily;
    }

    // createdAt comes from @CreationTimestamp (the real clock); the job reads it as server-local time
    private void setCreatedAt(Daily daily, Instant at) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE dailies SET created_at = :at WHERE id = :id")
                .setParameter("at", LocalDateTime.ofInstant(at, ZoneId.systemDefault()))
                .setParameter("id", daily.getId())
                .executeUpdate();
        entityManager.clear();
    }
}
