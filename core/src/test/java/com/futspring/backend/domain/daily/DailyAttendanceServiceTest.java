package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.notification.NotificationRepository;
import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.daily.repository.MatchRepository;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.support.InMemoryAttendance;
import com.futspring.backend.support.MembershipStubs;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyAttendanceServiceTest {

    @Mock
    NotificationRepository notificationRepository;

    // Session: Friday 2026-10-30 20:00 in São Paulo (23:00Z); diaristas confirm from Thursday 20:00 (2026-10-29T23:00Z)
    static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    static final Instant DIARISTA_OPENS = Instant.parse("2026-10-29T23:00:00Z");

    @Mock PeladaRepository peladaRepository;
    @Mock UserAuthenticationHelper userAuthHelper;
    @Mock DailyRepository dailyRepository;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock MatchRepository matchRepository;
    @Mock DailyAttendanceRepository attendanceRepository;
    @Mock DailyAttendanceBumpRepository bumpRepository;
    @Mock PeladaMensalistaRepository mensalistaRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    DailyAttendanceService attendanceService;
    InMemoryAttendance attendance;

    User admin;
    User member;
    User outsider;
    Pelada pelada;
    Daily scheduledDaily;

    @BeforeEach
    void setUp() {
        attendanceService = serviceAt(NOW);
        attendance = InMemoryAttendance.stub(attendanceRepository);

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").build();
        member = User.builder().id(2L).email("member@example.com").username("member").password("hash").build();
        outsider = User.builder().id(3L).email("out@example.com").username("out").password("hash").build();

        pelada = Pelada.builder()
                .id(10L)
                .name("Pelada")
                .dayOfWeek("FRIDAY")
                .timeOfDay("18:00")
                .duration(2f)
                .numberOfTeams(2)
                .playersPerTeam(1)
                .members(new HashSet<>(Set.of(admin, member)))
                .admins(new HashSet<>(Set.of(admin)))
                .creator(admin)
                .build();

        scheduledDaily = Daily.builder()
                .id(100L)
                .pelada(pelada)
                .dailyDate(LocalDate.of(2026, 10, 30))
                .dailyTime("20:00")
                .status(DailyStatus.SCHEDULED)
                .build();
        MembershipStubs.stubMembership(peladaRepository, pelada);
    }

    // --- confirmAttendance ---

    @Test
    void confirmAttendance_success_addsPlayer() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        DailyListItemDTO result = attendanceService.confirmAttendance(100L, "member@example.com");

        assertThat(attendance.users(scheduledDaily)).contains(member);
        assertThat(result).isNotNull();
    }

    @Test
    void confirmAttendance_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void confirmAttendance_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.confirmAttendance(999L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void confirmAttendance_notMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void confirmAttendance_lockedStatus_throwsBadRequest() {
        scheduledDaily.setStatus(DailyStatus.IN_COURSE);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void confirmAttendance_alreadyConfirmed_throwsConflict() {
        attendance.confirm(scheduledDaily, member);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // --- disconfirmAttendance ---

    @Test
    void disconfirmAttendance_success_removesPlayer() {
        attendance.confirm(scheduledDaily, member);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        attendanceService.disconfirmAttendance(100L, "member@example.com");

        assertThat(attendance.users(scheduledDaily)).doesNotContain(member);
    }

    @Test
    void disconfirmAttendance_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> attendanceService.disconfirmAttendance(100L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void disconfirmAttendance_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.disconfirmAttendance(999L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void disconfirmAttendance_lockedStatus_throwsBadRequest() {
        scheduledDaily.setStatus(DailyStatus.FINISHED);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.disconfirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void disconfirmAttendance_notConfirmed_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.disconfirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- adminConfirmAttendance ---

    @Test
    void adminConfirmAttendance_success_addsPlayer() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        DailyListItemDTO result = attendanceService.adminConfirmAttendance(100L, 2L, "admin@example.com");

        assertThat(attendance.users(scheduledDaily)).contains(member);
        assertThat(result).isNotNull();
    }

    @Test
    void adminConfirmAttendance_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(100L, 2L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void adminConfirmAttendance_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(100L, 2L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void adminConfirmAttendance_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(999L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void adminConfirmAttendance_targetNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(100L, 999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void adminConfirmAttendance_targetNotMember_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(100L, 3L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminConfirmAttendance_lockedStatus_throwsBadRequest() {
        scheduledDaily.setStatus(DailyStatus.IN_COURSE);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(100L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminConfirmAttendance_alreadyConfirmed_throwsConflict() {
        attendance.confirm(scheduledDaily, member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(100L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // --- adminDisconfirmAttendance ---

    @Test
    void adminDisconfirmAttendance_success_removesPlayer() {
        attendance.confirm(scheduledDaily, member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        DailyListItemDTO result = attendanceService.adminDisconfirmAttendance(100L, 2L, "admin@example.com");

        assertThat(attendance.users(scheduledDaily)).doesNotContain(member);
        assertThat(result).isNotNull();
        verify(eventPublisher).publishEvent(new AttendanceChangedByAdminEvent(scheduledDaily, admin, List.of(member), false));
    }

    @Test
    void adminDisconfirmAttendance_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.adminDisconfirmAttendance(100L, 2L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void adminDisconfirmAttendance_targetNotMember_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> attendanceService.adminDisconfirmAttendance(100L, 3L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminDisconfirmAttendance_lockedStatus_throwsBadRequest() {
        scheduledDaily.setStatus(DailyStatus.CANCELED);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> attendanceService.adminDisconfirmAttendance(100L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminDisconfirmAttendance_notConfirmed_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> attendanceService.adminDisconfirmAttendance(100L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- adminConfirmAll ---

    @Test
    void adminConfirmAll_confirmsOnlyTheMembersMissing() {
        attendance.confirm(scheduledDaily, member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        DailyListItemDTO result = attendanceService.adminConfirmAll(100L, "admin@example.com");

        assertThat(attendance.users(scheduledDaily)).containsExactly(member, admin);
        assertThat(result.confirmedPlayerCount()).isEqualTo(2);
    }

    @Test
    void adminConfirmAll_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.adminConfirmAll(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- clearAttendees ---

    @Test
    void clearAttendees_deletesTheSessionAttendance() {
        attendance.confirm(scheduledDaily, admin);
        attendance.confirm(scheduledDaily, member);

        attendanceService.clearAttendees(scheduledDaily);

        assertThat(attendance.users(scheduledDaily)).isEmpty();
    }

    // --- window, capacity and mensalista priority ---

    @Test
    void confirmAttendance_diaristaBeforeTheWindow_throwsForbiddenWithTheOpeningInThePeladaZone() {
        attendanceService = serviceAt(DIARISTA_OPENS.minusSeconds(1));
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .hasMessage("Diaristas podem confirmar a partir de qui, 29/10 às 20:00")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(attendance.users(scheduledDaily)).isEmpty();
    }

    @Test
    void confirmAttendance_diaristaExactlyWhenTheWindowOpens_confirms() {
        attendanceService = serviceAt(DIARISTA_OPENS);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        attendanceService.confirmAttendance(100L, "member@example.com");

        assertThat(attendance.rows(scheduledDaily)).singleElement()
                .satisfies(a -> assertThat(a.getConfirmedAt()).isEqualTo(DIARISTA_OPENS));
    }

    @Test
    void confirmAttendance_windowFollowsThePeladaTimeZone() {
        // 20:00 in Toronto (EDT, UTC-4) starts an hour after 20:00 in São Paulo (UTC-3), so it opens later
        Instant betweenTheOpenings = Instant.parse("2026-10-29T23:30:00Z");
        attendanceService = serviceAt(betweenTheOpenings);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        pelada.setTimeZone("America/Toronto");
        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "member@example.com"))
                .hasMessage("Diaristas podem confirmar a partir de qui, 29/10 às 20:00")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        pelada.setTimeZone("America/Sao_Paulo");
        attendanceService.confirmAttendance(100L, "member@example.com");
        assertThat(attendance.users(scheduledDaily)).containsExactly(member);
    }

    @Test
    void confirmAttendance_mensalistaBeforeTheWindow_confirms() {
        attendanceService = serviceAt(Instant.parse("2026-10-20T12:00:00Z"));
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(mensalistaRepository.existsByPeladaAndUser(pelada, member)).thenReturn(true);

        attendanceService.confirmAttendance(100L, "member@example.com");

        assertThat(attendance.users(scheduledDaily)).containsExactly(member);
    }

    @Test
    void confirmAttendance_diaristaIntoAFullSession_throwsConflict() {
        User d1 = addMember(4L);
        User d2 = addMember(5L);
        attendance.confirm(scheduledDaily, d1, d2);   // capacity 2 × 1
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "member@example.com"))
                .hasMessage("Sessão lotada")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(bumpRepository, eventPublisher);
    }

    @Test
    void confirmAttendance_mensalistaIntoAFullSession_bumpsTheVictimRecordsItAndPublishesTheEvent() {
        User d1 = addMember(4L);
        User d2 = addMember(5L);
        attendance.confirm(scheduledDaily, d1, d2);
        DailyAttendance lastDiarista = attendance.rows(scheduledDaily).get(1);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(mensalistaRepository.existsByPeladaAndUser(pelada, member)).thenReturn(true);
        when(attendanceRepository.findBumpVictim(scheduledDaily)).thenReturn(Optional.of(lastDiarista));
        stubBumpSave();

        DailyListItemDTO result = attendanceService.confirmAttendance(100L, "member@example.com");

        assertThat(attendance.users(scheduledDaily)).containsExactly(d1, member);
        assertThat(result.confirmedPlayerCount()).isEqualTo(2);
        ArgumentCaptor<DailyAttendanceBump> bump = ArgumentCaptor.forClass(DailyAttendanceBump.class);
        verify(bumpRepository).save(bump.capture());
        assertThat(bump.getValue().getUser()).isEqualTo(d2);
        assertThat(bump.getValue().getBumpedBy()).isEqualTo(member);
        assertThat(bump.getValue().getDaily()).isEqualTo(scheduledDaily);
        assertThat(bump.getValue().getBumpedAt()).isEqualTo(NOW);
        assertThat(bump.getValue().getSeenAt()).isNull();
        verify(eventPublisher).publishEvent(new DiaristaBumpedEvent(500L));
    }

    @Test
    void confirmAttendance_mensalistaBumpsADiaristaTheAdminConfirmed() {
        User d1 = addMember(4L);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(4L)).thenReturn(Optional.of(d1));
        attendance.confirm(scheduledDaily, admin);
        attendanceService.adminConfirmAttendance(100L, 4L, "admin@example.com");
        DailyAttendance adminConfirmed = attendance.rows(scheduledDaily).get(1);
        when(mensalistaRepository.existsByPeladaAndUser(pelada, member)).thenReturn(true);
        when(attendanceRepository.findBumpVictim(scheduledDaily)).thenReturn(Optional.of(adminConfirmed));
        stubBumpSave();

        attendanceService.confirmAttendance(100L, "member@example.com");

        assertThat(attendance.users(scheduledDaily)).containsExactlyInAnyOrder(admin, member);
        verify(eventPublisher).publishEvent(new DiaristaBumpedEvent(500L));
    }

    @Test
    void confirmAttendance_mensalistaIntoAFullSessionWithoutDiaristas_joinsOverCapacity() {
        User m1 = addMember(4L);
        User m2 = addMember(5L);
        attendance.confirm(scheduledDaily, m1, m2);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(mensalistaRepository.existsByPeladaAndUser(pelada, member)).thenReturn(true);
        when(attendanceRepository.findBumpVictim(scheduledDaily)).thenReturn(Optional.empty());

        DailyListItemDTO result = attendanceService.confirmAttendance(100L, "member@example.com");

        assertThat(result.confirmedPlayerCount()).isEqualTo(3);
        verifyNoInteractions(bumpRepository, eventPublisher);
    }

    @Test
    void adminConfirmAttendance_ignoresTheWindowAndTheCapacityAndBumpsNobody() {
        attendanceService = serviceAt(Instant.parse("2026-10-20T12:00:00Z"));
        User d1 = addMember(4L);
        attendance.confirm(scheduledDaily, admin, d1);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        DailyListItemDTO result = attendanceService.adminConfirmAttendance(100L, 2L, "admin@example.com");

        assertThat(result.confirmedPlayerCount()).isEqualTo(3);
        verify(attendanceRepository, never()).findBumpVictim(any());
        verifyNoInteractions(bumpRepository);
        // the only event is the notification of the confirmed player, no bump
        verify(eventPublisher).publishEvent(new AttendanceChangedByAdminEvent(scheduledDaily, admin, List.of(member), true));
        verifyNoMoreInteractions(eventPublisher);
        verify(mensalistaRepository, never()).existsByPeladaAndUser(pelada, member);
    }

    @Test
    void adminConfirmAll_goesPastTheCapacityAndBumpsNobody() {
        addMember(4L);
        addMember(5L);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        DailyListItemDTO result = attendanceService.adminConfirmAll(100L, "admin@example.com");

        assertThat(result.confirmedPlayerCount()).isEqualTo(4);
        verifyNoInteractions(bumpRepository);
        verify(eventPublisher).publishEvent(argThat((Object e) -> e instanceof AttendanceChangedByAdminEvent a
                && a.confirmed() && a.players().size() == 4));
        verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    void confirmAttendance_bumpedDiaristaCanConfirmAgainWhenASpotOpens() {
        User d1 = addMember(4L);
        User d2 = addMember(5L);
        attendance.confirm(scheduledDaily, d1, d2);
        DailyAttendance lastDiarista = attendance.rows(scheduledDaily).get(1);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(userAuthHelper.getAuthenticatedUser("d4@example.com")).thenReturn(d1);
        when(userAuthHelper.getAuthenticatedUser("d5@example.com")).thenReturn(d2);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(mensalistaRepository.existsByPeladaAndUser(pelada, member)).thenReturn(true);
        when(attendanceRepository.findBumpVictim(scheduledDaily)).thenReturn(Optional.of(lastDiarista));
        stubBumpSave();
        attendanceService.confirmAttendance(100L, "member@example.com");

        // Still full: refused like any diarista; once someone withdraws, d2 gets back in
        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "d5@example.com")).hasMessage("Sessão lotada");
        attendanceService.disconfirmAttendance(100L, "d4@example.com");
        attendanceService.confirmAttendance(100L, "d5@example.com");

        assertThat(attendance.users(scheduledDaily)).containsExactly(member, d2);
    }

    private DailyAttendanceService serviceAt(Instant now) {
        return new DailyAttendanceService(userAuthHelper, new PeladaAccessHelper(peladaRepository), dailyRepository,
                attendanceRepository, bumpRepository, mensalistaRepository, userRepository,
                new DailyListItemAssembler(attendanceRepository, teamRepository, matchRepository, mensalistaRepository), eventPublisher,
                Clock.fixed(now, ZoneOffset.UTC), notificationRepository);
    }

    private User addMember(long id) {
        User user = User.builder().id(id).email("d" + id + "@example.com").username("d" + id).password("hash").build();
        pelada.getMembers().add(user);
        return user;
    }

    private void stubBumpSave() {
        when(bumpRepository.save(any(DailyAttendanceBump.class))).thenAnswer(inv -> {
            DailyAttendanceBump bump = inv.getArgument(0);
            bump.setId(500L);
            return bump;
        });
    }
}
