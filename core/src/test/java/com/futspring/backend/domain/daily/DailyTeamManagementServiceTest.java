package com.futspring.backend.domain.daily;

import org.springframework.context.ApplicationEventPublisher;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStarsRepository;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO;
import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.daily.team.Team;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.team.TeamRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.support.InMemoryAttendance;
import com.futspring.backend.support.MembershipStubs;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.domain.daily.dto.SaveTeamsRequestDTO;
import com.futspring.backend.domain.daily.dto.SaveTeamsRequestDTO.TeamInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock PeladaRepository peladaRepository;
    @Mock UserAuthenticationHelper userAuthHelper;
    @Mock DailyRepository dailyRepository;
    @Mock DailyAttendanceRepository attendanceRepository;
    @Mock TeamRepository teamRepository;
    @Mock DailyDTOMapper dailyDTOMapper;
    @Mock UserRepository userRepository;

    @Mock
    PeladaPlayerStarsRepository playerStarsRepository;

    DailyTeamManagementService teamManagementService;
    InMemoryAttendance attendance;

    User admin;
    User member;
    User outsider;
    Pelada pelada;
    Daily scheduledDaily;

    @BeforeEach
    void setUp() {
        teamManagementService = new DailyTeamManagementService(
                userAuthHelper, new PeladaAccessHelper(peladaRepository), dailyRepository, attendanceRepository, teamRepository,
                playerStarsRepository, userRepository, dailyDTOMapper, eventPublisher,
                org.mockito.Mockito.mock(com.futspring.backend.domain.subscription.SubscriptionGuard.class));

        admin = User.builder().emailVerifiedAt(java.time.Instant.EPOCH).id(1L).email("admin@example.com").username("admin").password("hash").build();
        member = User.builder().emailVerifiedAt(java.time.Instant.EPOCH).id(2L).email("member@example.com").username("member").password("hash").build();
        outsider = User.builder().emailVerifiedAt(java.time.Instant.EPOCH).id(3L).email("out@example.com").username("out").password("hash").build();

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
                .status(DailyStatus.SCHEDULED)
                .build();
        MembershipStubs.stubMembership(peladaRepository, pelada);
        attendance = InMemoryAttendance.stub(attendanceRepository);
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
        scheduledDaily.setStatus(DailyStatus.IN_COURSE);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> teamManagementService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sortTeams_lockedStatusFinished_throwsBadRequest() {
        scheduledDaily.setStatus(DailyStatus.FINISHED);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> teamManagementService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sortTeams_lockedStatusCanceled_throwsBadRequest() {
        scheduledDaily.setStatus(DailyStatus.CANCELED);
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

        assertThatThrownBy(() -> teamManagementService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sortTeams_tooManyPlayers_throwsBadRequest() {
        // need exactly 2 (2 teams x 1), add 3
        attendance.confirm(scheduledDaily, admin);
        attendance.confirm(scheduledDaily, member);
        attendance.confirm(scheduledDaily, outsider);

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

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
    @SuppressWarnings("unchecked")
    void sortTeams_balancesByTheStarsInThisPelada() {
        // 5 + 1 vs 4 + 3: player3 has no row, so the default 3 stars
        User player3 = User.builder().emailVerifiedAt(java.time.Instant.EPOCH).id(4L).email("p3@example.com").username("p3").password("hash").build();
        User player4 = User.builder().emailVerifiedAt(java.time.Instant.EPOCH).id(5L).email("p4@example.com").username("p4").password("hash").build();
        pelada.setPlayersPerTeam(2);
        attendance.confirm(scheduledDaily, admin, member, player3, player4);

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(List.of());
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
        when(teamRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(playerStarsRepository.findStarsByPelada(pelada)).thenReturn(Map.of(1L, 5, 2L, 4, 5L, 1));

        teamManagementService.sortTeams(100L, "admin@example.com");

        ArgumentCaptor<List<Team>> saved = ArgumentCaptor.forClass(List.class);
        verify(teamRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Team::getPlayers)
                .containsExactlyInAnyOrder(Set.of(admin, player4), Set.of(member, player3));
    }

    @Test
    void sortTeams_success_deletesExistingTeamsAndCreatesNew() {
        attendance.confirm(scheduledDaily, admin);
        attendance.confirm(scheduledDaily, member);

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
        when(dailyDTOMapper.buildTeamDTO(any(Team.class), anyMap())).thenReturn(
                DailyDetailDTO.TeamDTO.builder().id(1L).name("Team 1").players(List.of()).build());

        List<DailyDetailDTO.TeamDTO> result = teamManagementService.sortTeams(100L, "admin@example.com");

        verify(teamRepository).deleteAll(List.of(existingTeam));
        assertThat(result).hasSize(2);
        verify(eventPublisher).publishEvent(argThat((Object e) -> e instanceof TeamsSortedEvent t
                && t.daily() == scheduledDaily && admin.equals(t.actor())
                && t.players().containsAll(List.of(admin, member)) && t.players().size() == 2));
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
        when(teamRepository.findByDailyWithPlayers(scheduledDaily)).thenReturn(List.of(team1, team2));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dailyDTOMapper.buildTeamDTO(any(Team.class), anyMap())).thenReturn(
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
        scheduledDaily.setStatus(DailyStatus.FINISHED);
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
        when(teamRepository.findByDailyWithPlayers(scheduledDaily)).thenReturn(List.of(team2));

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
        when(teamRepository.findByDailyWithPlayers(scheduledDaily)).thenReturn(List.of(team1));

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

    // --- saveTeams ---

    private User extra(long id, String name) {
        User user = User.builder().emailVerifiedAt(java.time.Instant.EPOCH).id(id).email(name + "@example.com").username(name).password("hash").build();
        pelada.getMembers().add(user);
        return user;
    }

    private void stubSave() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));
    }

    private static SaveTeamsRequestDTO request(TeamInput... teams) {
        return new SaveTeamsRequestDTO(List.of(teams));
    }

    @Test
    void saveTeams_differentSizesAndLeftovers_replacesTheTeamsInOrderAndPublishesTheEvent() {
        User ana = extra(4L, "Ana");
        User bruno = extra(5L, "Bruno");
        attendance.confirm(scheduledDaily, admin, member, ana, bruno);
        stubSave();
        when(teamRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        teamManagementService.saveTeams(100L, request(
                new TeamInput(" Azul ", "#3b82f6", List.of(1L, 2L, 4L)),
                new TeamInput("Verde", null, List.of(5L))), "admin@example.com");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Team>> saved = ArgumentCaptor.forClass(List.class);
        verify(teamRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Team::getName).containsExactly("Azul", "Verde");
        assertThat(saved.getValue().get(0).getColor()).isEqualTo("#3b82f6");
        assertThat(saved.getValue().get(0).getPlayers()).containsExactlyInAnyOrder(admin, member, ana);
        assertThat(saved.getValue().get(1).getPlayers()).containsExactly(bruno);
        verify(teamRepository).deleteAll(anyList());
        verify(eventPublisher).publishEvent(new TeamsSortedEvent(scheduledDaily, admin, List.of(admin, member, ana, bruno)));
    }

    @Test
    void saveTeams_confirmedPlayerLeftOut_isAllowed() {
        attendance.confirm(scheduledDaily, admin, member, extra(4L, "Ana"));
        stubSave();
        when(teamRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> teamManagementService.saveTeams(100L, request(
                new TeamInput("Time 1", null, List.of(1L)),
                new TeamInput("Time 2", null, List.of(2L))), "admin@example.com")).doesNotThrowAnyException();
    }

    @Test
    void saveTeams_playerTwice_throwsBadRequestNamingThem() {
        attendance.confirm(scheduledDaily, admin, member);
        stubSave();

        assertThatThrownBy(() -> teamManagementService.saveTeams(100L, request(
                new TeamInput("Time 1", null, List.of(1L, 2L)),
                new TeamInput("Time 2", null, List.of(2L))), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .hasMessage("member aparece em mais de um time")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(teamRepository, never()).saveAll(anyList());
    }

    @Test
    void saveTeams_playerNotConfirmed_throwsConflictNamingThem() {
        User ana = extra(4L, "Ana");
        attendance.confirm(scheduledDaily, admin, member);
        stubSave();
        when(userRepository.findById(4L)).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> teamManagementService.saveTeams(100L, request(
                new TeamInput("Time 1", null, List.of(1L, 4L)),
                new TeamInput("Time 2", null, List.of(2L))), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .hasMessage("Ana não está mais confirmada")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(teamRepository, never()).saveAll(anyList());
    }

    @Test
    void saveTeams_emptyTeam_throwsBadRequest() {
        attendance.confirm(scheduledDaily, admin, member);
        stubSave();

        assertThatThrownBy(() -> teamManagementService.saveTeams(100L, request(
                new TeamInput("Time 1", null, List.of(1L, 2L)),
                new TeamInput("Time 2", null, List.of()),
                new TeamInput("Time 3", null, List.of())), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .hasMessage("O Time 2 está vazio")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void saveTeams_lockedSession_throwsBadRequest() {
        scheduledDaily.setStatus(DailyStatus.IN_COURSE);
        stubSave();

        assertThatThrownBy(() -> teamManagementService.saveTeams(100L, request(
                new TeamInput("Time 1", null, List.of(1L)),
                new TeamInput("Time 2", null, List.of(2L))), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void saveTeams_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> teamManagementService.saveTeams(100L, request(
                new TeamInput("Time 1", null, List.of(1L)),
                new TeamInput("Time 2", null, List.of(2L))), "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void saveTeams_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamManagementService.saveTeams(999L, request(
                new TeamInput("Time 1", null, List.of(1L)),
                new TeamInput("Time 2", null, List.of(2L))), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- removeFromTeams ---

    @Test
    void removeFromTeams_takesThePlayerOutAndKeepsTheTeamEvenEmpty() {
        Team team1 = Team.builder().id(1L).daily(scheduledDaily).name("Time 1").players(new HashSet<>(Set.of(member))).build();
        Team team2 = Team.builder().id(2L).daily(scheduledDaily).name("Time 2").players(new HashSet<>(Set.of(admin))).build();
        when(teamRepository.findByDailyInWithPlayers(List.of(scheduledDaily))).thenReturn(List.of(team1, team2));

        teamManagementService.removeFromTeams(scheduledDaily, member);

        assertThat(team1.getPlayers()).isEmpty();
        assertThat(team2.getPlayers()).containsExactly(admin);
        verify(teamRepository, never()).delete(any());
        verify(teamRepository, never()).deleteAll(anyList());
    }
}
