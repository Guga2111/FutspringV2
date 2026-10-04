package com.futspring.backend.service;

import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.DailyRepository;
import com.futspring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyAttendanceServiceTest {

    @Mock UserAuthenticationHelper userAuthHelper;
    @Mock DailyRepository dailyRepository;
    @Mock UserRepository userRepository;

    DailyAttendanceService attendanceService;

    User admin;
    User member;
    User outsider;
    Pelada pelada;
    Daily scheduledDaily;

    @BeforeEach
    void setUp() {
        attendanceService = new DailyAttendanceService(userAuthHelper, dailyRepository, userRepository);

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").stars(4).build();
        member = User.builder().id(2L).email("member@example.com").username("member").password("hash").stars(3).build();
        outsider = User.builder().id(3L).email("out@example.com").username("out").password("hash").stars(3).build();

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
                .dailyDate(LocalDate.now().plusDays(3))
                .dailyTime("18:00")
                .status("SCHEDULED")
                .confirmedPlayers(new HashSet<>())
                .build();
    }

    // --- confirmAttendance ---

    @Test
    void confirmAttendance_success_addsPlayer() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DailyListItemDTO result = attendanceService.confirmAttendance(100L, "member@example.com");

        assertThat(scheduledDaily.getConfirmedPlayers()).contains(member);
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
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.confirmAttendance(999L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void confirmAttendance_notMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void confirmAttendance_lockedStatus_throwsBadRequest() {
        scheduledDaily.setStatus("IN_COURSE");
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void confirmAttendance_alreadyConfirmed_throwsConflict() {
        scheduledDaily.getConfirmedPlayers().add(member);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> attendanceService.confirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // --- disconfirmAttendance ---

    @Test
    void disconfirmAttendance_success_removesPlayer() {
        scheduledDaily.getConfirmedPlayers().add(member);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        attendanceService.disconfirmAttendance(100L, "member@example.com");

        assertThat(scheduledDaily.getConfirmedPlayers()).doesNotContain(member);
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
        scheduledDaily.setStatus("FINISHED");
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
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DailyListItemDTO result = attendanceService.adminConfirmAttendance(100L, 2L, "admin@example.com");

        assertThat(scheduledDaily.getConfirmedPlayers()).contains(member);
        assertThat(result).isNotNull();
    }

    @Test
    void adminConfirmAttendance_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

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
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(999L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void adminConfirmAttendance_targetNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(100L, 999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void adminConfirmAttendance_targetNotMember_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(100L, 3L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminConfirmAttendance_lockedStatus_throwsBadRequest() {
        scheduledDaily.setStatus("IN_COURSE");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(100L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminConfirmAttendance_alreadyConfirmed_throwsConflict() {
        scheduledDaily.getConfirmedPlayers().add(member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> attendanceService.adminConfirmAttendance(100L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // --- adminDisconfirmAttendance ---

    @Test
    void adminDisconfirmAttendance_success_removesPlayer() {
        scheduledDaily.getConfirmedPlayers().add(member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DailyListItemDTO result = attendanceService.adminDisconfirmAttendance(100L, 2L, "admin@example.com");

        assertThat(scheduledDaily.getConfirmedPlayers()).doesNotContain(member);
        assertThat(result).isNotNull();
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
        scheduledDaily.setStatus("CANCELED");
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

    // --- clearAttendees ---

    @Test
    void clearAttendees_clearsConfirmedPlayersAndSaves() {
        scheduledDaily.getConfirmedPlayers().add(admin);
        scheduledDaily.getConfirmedPlayers().add(member);

        attendanceService.clearAttendees(scheduledDaily);

        assertThat(scheduledDaily.getConfirmedPlayers()).isEmpty();
        verify(dailyRepository).save(scheduledDaily);
    }
}
