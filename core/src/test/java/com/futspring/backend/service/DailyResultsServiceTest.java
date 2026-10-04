package com.futspring.backend.service;

import com.futspring.backend.dto.DailyDetailDTO.MatchDTO;
import com.futspring.backend.dto.MatchResultDTO;
import com.futspring.backend.entity.*;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.*;
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
class DailyResultsServiceTest {

    @Mock FileUploadService fileUploadService;
    @Mock UserAuthenticationHelper userAuthHelper;
    @Mock DailyRepository dailyRepository;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock MatchRepository matchRepository;
    @Mock PlayerMatchStatRepository playerMatchStatRepository;
    @Mock UserDailyStatsRepository userDailyStatsRepository;
    @Mock LeagueTableEntryRepository leagueTableEntryRepository;
    @Mock DailyAwardRepository dailyAwardRepository;
    @Mock StatsRepository statsRepository;
    @Mock RankingRepository rankingRepository;

    DailyResultsService resultsService;

    User admin;
    User member;
    Pelada pelada;
    Daily inCourseDaily;
    Team team1;
    Team team2;

    @BeforeEach
    void setUp() {
        resultsService = new DailyResultsService(
                fileUploadService, userAuthHelper, dailyRepository, userRepository,
                teamRepository, matchRepository, playerMatchStatRepository,
                userDailyStatsRepository, leagueTableEntryRepository,
                dailyAwardRepository, statsRepository, rankingRepository);

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").stars(4).build();
        member = User.builder().id(2L).email("member@example.com").username("member").password("hash").stars(3).build();

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

        team1 = Team.builder().id(1L).name("Red").color("#FF0000").players(new HashSet<>(Set.of(admin))).build();
        team2 = Team.builder().id(2L).name("Blue").color("#0000FF").players(new HashSet<>(Set.of(member))).build();

        inCourseDaily = Daily.builder()
                .id(100L)
                .pelada(pelada)
                .dailyDate(LocalDate.now())
                .dailyTime("18:00")
                .status("IN_COURSE")
                .confirmedPlayers(new HashSet<>(Set.of(admin, member)))
                .build();
    }

    // --- submitResults ---

    @Test
    void submitResults_success_returnsMatchDTOs() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(teamRepository.findByDaily(inCourseDaily)).thenReturn(List.of(team1, team2));
        when(matchRepository.findById(anyLong())).thenReturn(Optional.empty());

        Match savedMatch = Match.builder().id(50L).daily(inCourseDaily).team1(team1).team2(team2)
                .team1Score(2).team2Score(1).winner(team1).build();
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);
        when(playerMatchStatRepository.saveAll(any())).thenReturn(List.of());

        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(List.of(savedMatch));
        when(leagueTableEntryRepository.findByDailyOrderByPositionAsc(inCourseDaily)).thenReturn(List.of());
        when(leagueTableEntryRepository.saveAll(any())).thenReturn(List.of());

        MatchResultDTO result = new MatchResultDTO();
        result.setTeam1Id(1L);
        result.setTeam2Id(2L);
        result.setTeam1Score(2);
        result.setTeam2Score(1);

        List<MatchDTO> response = resultsService.submitResults(100L, List.of(result), "admin@example.com");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getTeam1Score()).isEqualTo(2);
        assertThat(response.get(0).getTeam2Score()).isEqualTo(1);
    }

    @Test
    void submitResults_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));

        assertThatThrownBy(() -> resultsService.submitResults(100L, List.of(), "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void submitResults_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> resultsService.submitResults(100L, List.of(), "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void submitResults_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resultsService.submitResults(999L, List.of(), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void submitResults_invalidStatus_throwsBadRequest() {
        inCourseDaily.setStatus("SCHEDULED");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));

        assertThatThrownBy(() -> resultsService.submitResults(100L, List.of(), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void submitResults_invalidTeamIds_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(teamRepository.findByDaily(inCourseDaily)).thenReturn(List.of(team1, team2));

        MatchResultDTO result = new MatchResultDTO();
        result.setTeam1Id(99L); // unknown team
        result.setTeam2Id(2L);
        result.setTeam1Score(1);
        result.setTeam2Score(0);

        assertThatThrownBy(() -> resultsService.submitResults(100L, List.of(result), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- finalizeDaily ---

    @Test
    void finalizeDaily_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));

        assertThatThrownBy(() -> resultsService.finalizeDaily(100L, null, null, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void finalizeDaily_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> resultsService.finalizeDaily(100L, null, null, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void finalizeDaily_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resultsService.finalizeDaily(999L, null, null, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void finalizeDaily_invalidStatus_throwsBadRequest() {
        inCourseDaily.setStatus("SCHEDULED");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));

        assertThatThrownBy(() -> resultsService.finalizeDaily(100L, null, null, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void finalizeDaily_noMatches_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> resultsService.finalizeDaily(100L, null, null, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void finalizeDaily_invalidPuskasWinner_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        Match match = Match.builder().id(50L).daily(inCourseDaily).team1(team1).team2(team2)
                .team1Score(1).team2Score(0).winner(team1).build();
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(List.of(match));

        // puskas winner id 99 is not a confirmed player
        assertThatThrownBy(() -> resultsService.finalizeDaily(100L, List.of(99L), null, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void finalizeDaily_success_marksFinishedAndSavesStats() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));

        Match match = Match.builder().id(50L).daily(inCourseDaily).team1(team1).team2(team2)
                .team1Score(2).team2Score(0).winner(team1).build();
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(List.of(match));
        when(userDailyStatsRepository.findByDaily(inCourseDaily)).thenReturn(Collections.emptyList());
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));
        when(playerMatchStatRepository.findByMatchInWithUser(any())).thenReturn(Collections.emptyList());
        when(leagueTableEntryRepository.findByDailyOrderByPositionAsc(inCourseDaily)).thenReturn(Collections.emptyList());
        when(leagueTableEntryRepository.saveAll(any())).thenReturn(Collections.emptyList());
        when(dailyAwardRepository.findByDaily(inCourseDaily)).thenReturn(Optional.empty());
        when(dailyAwardRepository.save(any())).thenReturn(null);
        when(rankingRepository.findByPeladaAndUserIn(eq(pelada), any())).thenReturn(Collections.emptyList());
        when(statsRepository.findByUserIn(any())).thenReturn(Collections.emptyList());
        when(userDailyStatsRepository.aggregateRankingByUsersAndPelada(any(), eq(pelada))).thenReturn(Collections.emptyList());
        when(userDailyStatsRepository.aggregateStatsByUsers(any())).thenReturn(Collections.emptyList());
        when(dailyAwardRepository.findPuskasDatesByUsers(any())).thenReturn(Collections.emptyList());
        when(dailyRepository.save(any())).thenReturn(inCourseDaily);
        when(userDailyStatsRepository.saveAll(any())).thenReturn(Collections.emptyList());

        resultsService.finalizeDaily(100L, null, null, "admin@example.com");

        assertThat(inCourseDaily.getStatus()).isEqualTo("FINISHED");
        assertThat(inCourseDaily.isFinished()).isTrue();
        verify(dailyRepository).save(inCourseDaily);
        verify(rankingRepository).saveAll(any());
        verify(statsRepository).saveAll(any());
    }
}
