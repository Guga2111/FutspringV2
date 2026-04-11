package com.futspring.backend.service;

import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.Team;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.DailyRepository;
import com.futspring.backend.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyTeamManagementServiceTest {

    @Mock UserAuthenticationHelper userAuthHelper;
    @Mock DailyRepository dailyRepository;
    @Mock TeamRepository teamRepository;
    @Mock DailyDTOMapper dailyDTOMapper;

    DailyTeamManagementService teamManagementService;

    User admin;
    User member;
    User outsider;
    Pelada pelada;
    Daily scheduledDaily;

    @BeforeEach
    void setUp() {
        teamManagementService = new DailyTeamManagementService(
                userAuthHelper, dailyRepository, teamRepository, dailyDTOMapper);

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").stars(4).build();
        member = User.builder().id(2L).email("member@example.com").username("member").password("hash").stars(3).build();
        outsider = User.builder().id(3L).email("out@example.com").username("out").password("hash").stars(2).build();

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

    // --- sortTeams error paths ---

    @Test
    void sortTeams_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> teamManagementService.sortTeams(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void sortTeams_lockedStatusInCourse_throwsBadRequest() {
        scheduledDaily.setStatus("IN_COURSE");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> teamManagementService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sortTeams_lockedStatusFinished_throwsBadRequest() {
        scheduledDaily.setStatus("FINISHED");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> teamManagementService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sortTeams_lockedStatusCanceled_throwsBadRequest() {
        scheduledDaily.setStatus("CANCELED");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> teamManagementService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sortTeams_tooFewPlayers_throwsBadRequest() {
        // need exactly 2 (2 teams x 1), but 0 confirmed
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> teamManagementService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sortTeams_tooManyPlayers_throwsBadRequest() {
        // need exactly 2 (2 teams x 1), add 3
        scheduledDaily.getConfirmedPlayers().add(admin);
        scheduledDaily.getConfirmedPlayers().add(member);
        scheduledDaily.getConfirmedPlayers().add(outsider);

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> teamManagementService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sortTeams_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamManagementService.sortTeams(999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void sortTeams_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> teamManagementService.sortTeams(100L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void sortTeams_success_deletesExistingTeamsAndCreatesNew() {
        scheduledDaily.getConfirmedPlayers().add(admin);
        scheduledDaily.getConfirmedPlayers().add(member);

        Team existingTeam = Team.builder().id(1L).daily(scheduledDaily).name("Old Team")
                .players(new HashSet<>()).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(List.of(existingTeam));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            if (t.getId() == null) ReflectionTestUtils.setField(t, "id", (long)(Math.random() * 1000 + 10));
            return t;
        });
        when(teamRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(dailyDTOMapper.buildTeamDTO(any(Team.class))).thenReturn(
                DailyDetailDTO.TeamDTO.builder().id(1L).name("Team 1").players(List.of()).build());

        List<DailyDetailDTO.TeamDTO> result = teamManagementService.sortTeams(100L, "admin@example.com");

        verify(teamRepository).deleteAll(List.of(existingTeam));
        assertThat(result).hasSize(2);
    }

    // --- swapPlayers swap logic ---

    @Test
    void swapPlayers_success_swapsPlayersBetweenTeams() {
        Team team1 = Team.builder().id(1L).daily(scheduledDaily).name("Team 1")
                .players(new HashSet<>(Set.of(admin))).build();
        Team team2 = Team.builder().id(2L).daily(scheduledDaily).name("Team 2")
                .players(new HashSet<>(Set.of(member))).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(List.of(team1, team2));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dailyDTOMapper.buildTeamDTO(any(Team.class))).thenReturn(
                DailyDetailDTO.TeamDTO.builder().id(1L).name("Team").players(List.of()).build());

        teamManagementService.swapPlayers(100L, 1L, 2L, "admin@example.com");

        assertThat(team1.getPlayers()).contains(member).doesNotContain(admin);
        assertThat(team2.getPlayers()).contains(admin).doesNotContain(member);
        verify(teamRepository).save(team1);
        verify(teamRepository).save(team2);
    }

    @Test
    void swapPlayers_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> teamManagementService.swapPlayers(100L, 1L, 2L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void swapPlayers_lockedStatus_throwsBadRequest() {
        scheduledDaily.setStatus("FINISHED");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> teamManagementService.swapPlayers(100L, 1L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void swapPlayers_player1NotOnAnyTeam_throwsBadRequest() {
        Team team2 = Team.builder().id(2L).daily(scheduledDaily).name("Team 2")
                .players(new HashSet<>(Set.of(member))).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(List.of(team2));

        assertThatThrownBy(() -> teamManagementService.swapPlayers(100L, 999L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void swapPlayers_player2NotOnAnyTeam_throwsBadRequest() {
        Team team1 = Team.builder().id(1L).daily(scheduledDaily).name("Team 1")
                .players(new HashSet<>(Set.of(admin))).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(List.of(team1));

        assertThatThrownBy(() -> teamManagementService.swapPlayers(100L, 1L, 999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void swapPlayers_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamManagementService.swapPlayers(999L, 1L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void swapPlayers_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> teamManagementService.swapPlayers(100L, 1L, 2L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
