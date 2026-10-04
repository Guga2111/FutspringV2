package com.futspring.backend.service;

import com.futspring.backend.support.MembershipStubs;
import com.futspring.backend.helper.PeladaAccessHelper;
import com.futspring.backend.dto.DailyDetailDTO.MatchDTO;
import com.futspring.backend.dto.MatchResultDTO;
import com.futspring.backend.dto.PopulateDailyRequestDTO;
import com.futspring.backend.entity.*;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock PeladaRepository peladaRepository;
    @Mock DailyTeamManagementService dailyTeamManagementService;
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
                fileUploadService, userAuthHelper, new PeladaAccessHelper(peladaRepository),
                new AggregateRebuildService(userDailyStatsRepository, dailyAwardRepository, rankingRepository, statsRepository),
                dailyTeamManagementService, new DailyDTOMapper(), dailyRepository, userRepository,
                teamRepository, matchRepository, playerMatchStatRepository,
                userDailyStatsRepository, leagueTableEntryRepository, dailyAwardRepository);

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
                .status(DailyStatus.IN_COURSE)
                .confirmedPlayers(new HashSet<>(Set.of(admin, member)))
                .build();
        MembershipStubs.stubMembership(peladaRepository, pelada);
    }

    // --- submitResults ---

    @Test
    void submitResults_success_returnsMatchDTOs() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));

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
    void submitResults_matchIdFromAnotherDaily_throwsNotFoundAndChangesNothing() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));
        // match 999 exists, but in another pelada's daily: the scoped lookup does not find it
        when(matchRepository.findByIdAndDaily(999L, inCourseDaily)).thenReturn(Optional.empty());

        MatchResultDTO result = new MatchResultDTO();
        result.setMatchId(999L);
        result.setTeam1Id(1L);
        result.setTeam2Id(2L);
        result.setTeam1Score(5);
        result.setTeam2Score(0);

        assertThatThrownBy(() -> resultsService.submitResults(100L, List.of(result), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(matchRepository, never()).findById(any());
        verify(matchRepository, never()).save(any());
        verify(playerMatchStatRepository, never()).deleteByMatch(any());
        verify(playerMatchStatRepository, never()).saveAll(any());
    }

    @Test
    void submitResults_statsForPlayerOutsideTheMatch_throwsBadRequest() {
        User stranger = User.builder().id(9L).email("x@example.com").username("x").password("hash").build();
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));

        MatchResultDTO result = new MatchResultDTO();
        result.setTeam1Id(1L);
        result.setTeam2Id(2L);
        result.setTeam1Score(1);
        result.setTeam2Score(0);
        result.setPlayerStats(List.of(new MatchResultDTO.PlayerStatInputDTO(stranger.getId(), 1, 0)));

        assertThatThrownBy(() -> resultsService.submitResults(100L, List.of(result), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(matchRepository, never()).save(any());
    }

    @Test
    void submitResults_sameTeamTwice_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));

        MatchResultDTO result = new MatchResultDTO();
        result.setTeam1Id(1L);
        result.setTeam2Id(1L);

        assertThatThrownBy(() -> resultsService.submitResults(100L, List.of(result), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitResults_savesStatsOnlyForPlayersOfTheTwoTeams() {
        User benched = User.builder().id(3L).email("bench@example.com").username("bench").password("hash").stars(2).build();
        Team team3 = Team.builder().id(3L).name("Green").color("#00FF00").players(new HashSet<>(Set.of(benched))).build();
        inCourseDaily.getConfirmedPlayers().add(benched);

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2, team3));
        Match savedMatch = Match.builder().id(50L).daily(inCourseDaily).team1(team1).team2(team2)
                .team1Score(1).team2Score(0).winner(team1).build();
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2, team3));
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(List.of(savedMatch));
        when(leagueTableEntryRepository.findByDailyOrderByPositionAsc(inCourseDaily)).thenReturn(List.of());

        MatchResultDTO result = new MatchResultDTO(null, 1L, 2L, 1, 0,
                List.of(new MatchResultDTO.PlayerStatInputDTO(1L, 1, 0)));

        resultsService.submitResults(100L, List.of(result), "admin@example.com");

        ArgumentCaptor<List<PlayerMatchStat>> captor = ArgumentCaptor.forClass(List.class);
        verify(playerMatchStatRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(stat -> stat.getUser().getId())
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(captor.getValue())
                .filteredOn(stat -> stat.getUser().getId().equals(1L))
                .extracting(PlayerMatchStat::getGoals)
                .containsExactly(1);
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
        inCourseDaily.setStatus(DailyStatus.SCHEDULED);
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
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));

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
        inCourseDaily.setStatus(DailyStatus.SCHEDULED);
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

        // puskas winner id 99 is not on any of the session's teams
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

        assertThat(inCourseDaily.getStatus()).isEqualTo(DailyStatus.FINISHED);
        assertThat(inCourseDaily.isFinished()).isTrue();
        verify(dailyRepository).save(inCourseDaily);
        verify(rankingRepository).saveAll(any());
        verify(statsRepository).saveAll(any());
    }

    @Test
    void finalizeDaily_usesTeamPlayersNotTheConfirmedList() {
        // member is on team2 but un-confirmed after the sort; bench confirmed after the sort and is on no team
        User bench = User.builder().id(3L).email("bench@example.com").username("bench").password("hash").build();
        inCourseDaily.setConfirmedPlayers(new HashSet<>(Set.of(admin, bench)));
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));

        Match match = Match.builder().id(50L).daily(inCourseDaily).team1(team1).team2(team2)
                .team1Score(0).team2Score(1).winner(team2).build();
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(List.of(match));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));
        when(playerMatchStatRepository.findByMatchInWithUser(any())).thenReturn(List.of(
                PlayerMatchStat.builder().match(match).user(admin).goals(0).assists(0).build(),
                PlayerMatchStat.builder().match(match).user(member).goals(1).assists(0).build()));
        when(dailyAwardRepository.findByDaily(inCourseDaily)).thenReturn(Optional.empty());
        stubCloseSession(Collections.emptyList());

        resultsService.finalizeDaily(100L, List.of(2L), null, "admin@example.com");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserDailyStats>> statsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(userDailyStatsRepository).saveAll(statsCaptor.capture());
        Map<Long, UserDailyStats> byUser = new HashMap<>();
        statsCaptor.getValue().forEach(uds -> byUser.put(uds.getUser().getId(), uds));
        assertThat(byUser).containsOnlyKeys(1L, 2L);
        assertThat(byUser.get(2L).getGoals()).isEqualTo(1);
        assertThat(byUser.get(2L).getMatchesPlayed()).isEqualTo(1);
        assertThat(byUser.get(2L).isWonSession()).isTrue();

        ArgumentCaptor<DailyAward> awardCaptor = ArgumentCaptor.forClass(DailyAward.class);
        verify(dailyAwardRepository).save(awardCaptor.capture());
        assertThat(awardCaptor.getValue().getArtilheiroWinners()).containsExactly(member);
        assertThat(awardCaptor.getValue().getPuskasWinners()).containsExactly(member);
    }

    @Test
    void finalizeDaily_puskasWinnerNotOnATeam_throwsBadRequest() {
        User bench = User.builder().id(3L).email("bench@example.com").username("bench").password("hash").build();
        inCourseDaily.getConfirmedPlayers().add(bench);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        Match match = Match.builder().id(50L).daily(inCourseDaily).team1(team1).team2(team2)
                .team1Score(1).team2Score(0).winner(team1).build();
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(List.of(match));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));

        assertThatThrownBy(() -> resultsService.finalizeDaily(100L, List.of(3L), null, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(userDailyStatsRepository, never()).saveAll(any());
    }

    @Test
    void finalizeDaily_again_rebuildsPlayersOfThePreviousFinalize() {
        User former = User.builder().id(4L).email("former@example.com").username("former").password("hash").build();
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        Match match = Match.builder().id(50L).daily(inCourseDaily).team1(team1).team2(team2)
                .team1Score(1).team2Score(0).winner(team1).build();
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(List.of(match));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));
        when(playerMatchStatRepository.findByMatchInWithUser(any())).thenReturn(Collections.emptyList());
        when(dailyAwardRepository.findByDaily(inCourseDaily)).thenReturn(Optional.empty());
        stubCloseSession(List.of(UserDailyStats.builder().daily(inCourseDaily).user(former).build()));

        resultsService.finalizeDaily(100L, null, null, "admin@example.com");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<User>> playersCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(statsRepository).findByUserIn(playersCaptor.capture());
        assertThat(playersCaptor.getValue()).containsExactlyInAnyOrder(admin, member, former);
    }

    // Stubs the rest of the close-session pipeline (stats, league table, award save, Ranking/Stats rebuild)
    private void stubCloseSession(List<UserDailyStats> previousStats) {
        when(userDailyStatsRepository.findByDaily(inCourseDaily)).thenReturn(previousStats);
        when(leagueTableEntryRepository.findByDailyOrderByPositionAsc(inCourseDaily)).thenReturn(Collections.emptyList());
        when(leagueTableEntryRepository.saveAll(any())).thenReturn(Collections.emptyList());
        when(dailyAwardRepository.save(any())).thenReturn(null);
        when(rankingRepository.findByPeladaAndUserIn(eq(pelada), any())).thenReturn(Collections.emptyList());
        when(statsRepository.findByUserIn(any())).thenReturn(Collections.emptyList());
        when(userDailyStatsRepository.aggregateRankingByUsersAndPelada(any(), eq(pelada))).thenReturn(Collections.emptyList());
        when(userDailyStatsRepository.aggregateStatsByUsers(any())).thenReturn(Collections.emptyList());
        when(dailyAwardRepository.findPuskasDatesByUsers(any())).thenReturn(Collections.emptyList());
        when(dailyRepository.save(any())).thenReturn(inCourseDaily);
        when(userDailyStatsRepository.saveAll(any())).thenReturn(Collections.emptyList());
    }

    // --- submitResults: replace semantics and finished sessions ---

    @Test
    void submitResults_savedMatchLeftOut_isDeleted() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));

        Match kept = Match.builder().id(50L).daily(inCourseDaily).team1(team1).team2(team2).team1Score(0).team2Score(0).build();
        Match removed = Match.builder().id(51L).daily(inCourseDaily).team1(team1).team2(team2).team1Score(3).team2Score(0).build();
        when(matchRepository.findByIdAndDaily(50L, inCourseDaily)).thenReturn(Optional.of(kept));
        when(matchRepository.save(kept)).thenReturn(kept);
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(List.of(kept, removed));
        when(leagueTableEntryRepository.findByDailyOrderByPositionAsc(inCourseDaily)).thenReturn(List.of());

        MatchResultDTO result = new MatchResultDTO();
        result.setMatchId(50L);
        result.setTeam1Id(1L);
        result.setTeam2Id(2L);
        result.setTeam1Score(1);
        result.setTeam2Score(0);

        resultsService.submitResults(100L, List.of(result), "admin@example.com");

        verify(playerMatchStatRepository).deleteByMatchIn(List.of(removed));
        verify(matchRepository).deleteAll(List.of(removed));
        // the league table only counts the kept match
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LeagueTableEntry>> tableCaptor = ArgumentCaptor.forClass(List.class);
        verify(leagueTableEntryRepository).saveAll(tableCaptor.capture());
        assertThat(tableCaptor.getValue().get(0).getTeam()).isEqualTo(team1);
        assertThat(tableCaptor.getValue().get(0).getGoalsFor()).isEqualTo(1);
    }

    @Test
    void submitResults_sameMatchIdTwice_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));
        Match existing = Match.builder().id(50L).daily(inCourseDaily).team1(team1).team2(team2).build();
        when(matchRepository.findByIdAndDaily(50L, inCourseDaily)).thenReturn(Optional.of(existing));
        when(matchRepository.save(existing)).thenReturn(existing);

        MatchResultDTO result = new MatchResultDTO();
        result.setMatchId(50L);
        result.setTeam1Id(1L);
        result.setTeam2Id(2L);
        result.setTeam1Score(1);
        result.setTeam2Score(0);

        assertThatThrownBy(() -> resultsService.submitResults(100L, List.of(result, result), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void submitResults_onFinishedDaily_recomputesStatsAndKeepsVotedAwards() {
        inCourseDaily.setStatus(DailyStatus.FINISHED);
        inCourseDaily.setFinished(true);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(teamRepository.findByDailyWithPlayers(inCourseDaily)).thenReturn(List.of(team1, team2));

        Match match = Match.builder().id(50L).daily(inCourseDaily).team1(team1).team2(team2).team1Score(1).team2Score(0).build();
        when(matchRepository.findByIdAndDaily(50L, inCourseDaily)).thenReturn(Optional.of(match));
        when(matchRepository.save(match)).thenReturn(match);
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(List.of(match));
        when(playerMatchStatRepository.findByMatchInWithUser(any())).thenReturn(List.of(
                PlayerMatchStat.builder().match(match).user(member).goals(0).assists(0).build(),
                PlayerMatchStat.builder().match(match).user(admin).goals(0).assists(0).build()));
        DailyAward award = DailyAward.builder().daily(inCourseDaily).build();
        award.getPuskasWinners().add(member);
        when(dailyAwardRepository.findByDaily(inCourseDaily)).thenReturn(Optional.of(award));
        stubCloseSession(Collections.emptyList());

        MatchResultDTO result = new MatchResultDTO();
        result.setMatchId(50L);
        result.setTeam1Id(1L);
        result.setTeam2Id(2L);
        result.setTeam1Score(0);
        result.setTeam2Score(2);
        MatchResultDTO.PlayerStatInputDTO goals = new MatchResultDTO.PlayerStatInputDTO();
        goals.setUserId(2L);
        goals.setGoals(2);
        goals.setAssists(0);
        result.setPlayerStats(List.of(goals));

        resultsService.submitResults(100L, List.of(result), "admin@example.com");

        verify(userDailyStatsRepository).saveAll(any());
        verify(rankingRepository).saveAll(any());
        verify(statsRepository).saveAll(any());
        assertThat(award.getPuskasWinners()).containsExactly(member);
        assertThat(inCourseDaily.getStatus()).isEqualTo(DailyStatus.FINISHED);
    }

    // --- populateFromMessage ---

    @Test
    void populateFromMessage_matchBetweenTheSameTeam_throwsBadRequest() {
        inCourseDaily.setStatus(DailyStatus.CONFIRMED);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(inCourseDaily));
        when(userRepository.findAllById(any())).thenReturn(List.of(admin, member));
        when(matchRepository.findByDaily(inCourseDaily)).thenReturn(List.of());
        long[] nextId = {1};
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(nextId[0]++);
            return t;
        });

        PopulateDailyRequestDTO request = new PopulateDailyRequestDTO();
        request.setTeams(List.of(parsedTeam("Azul", admin), parsedTeam("Vermelho", member)));
        PopulateDailyRequestDTO.ParsedMatchDTO match = new PopulateDailyRequestDTO.ParsedMatchDTO();
        match.setTeam1ColorName("Azul");
        match.setTeam2ColorName("azul");
        match.setTeam1Score(2);
        match.setTeam2Score(1);
        request.setMatches(List.of(match));

        assertThatThrownBy(() -> resultsService.populateFromMessage(100L, request, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(matchRepository, never()).save(any());
    }

    private static PopulateDailyRequestDTO.ParsedTeamDTO parsedTeam(String color, User player) {
        PopulateDailyRequestDTO.ParsedPlayerDTO parsedPlayer = new PopulateDailyRequestDTO.ParsedPlayerDTO();
        parsedPlayer.setUserId(player.getId());
        PopulateDailyRequestDTO.ParsedTeamDTO team = new PopulateDailyRequestDTO.ParsedTeamDTO();
        team.setColorName(color);
        team.setColorHex("#0000FF");
        team.setPlayers(List.of(parsedPlayer));
        return team;
    }
}
