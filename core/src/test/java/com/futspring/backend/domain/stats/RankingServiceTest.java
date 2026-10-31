package com.futspring.backend.domain.stats;

import com.futspring.backend.domain.pelada.PeladaPlayerStarsRepository;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.stats.dto.PlayerPeladaHistoryDTO;
import com.futspring.backend.domain.stats.dto.PlayerPeladaStatsDTO;
import com.futspring.backend.domain.stats.dto.RankingDTO;
import com.futspring.backend.domain.stats.entity.Ranking;
import com.futspring.backend.domain.stats.entity.UserDailyStats;
import com.futspring.backend.domain.stats.repository.DailyAwardRepository;
import com.futspring.backend.domain.stats.repository.RankingRepository;
import com.futspring.backend.domain.stats.repository.UserDailyStatsRepository;
import com.futspring.backend.support.MembershipStubs;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    PeladaRepository peladaRepository;
    @Mock
    UserAuthenticationHelper userAuthHelper;
    @Mock
    RankingRepository rankingRepository;
    @Mock
    DailyAwardRepository dailyAwardRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    UserDailyStatsRepository userDailyStatsRepository;

    @Mock
    PeladaPlayerStarsRepository playerStarsRepository;

    RankingService rankingService;

    User admin;
    User player;
    User outsider;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(peladaRepository, userAuthHelper, new PeladaAccessHelper(peladaRepository), rankingRepository, dailyAwardRepository, userRepository, userDailyStatsRepository, playerStarsRepository);

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").build();
        player = User.builder().id(2L).email("player@example.com").username("player").password("hash").build();
        outsider = User.builder().id(3L).email("out@example.com").username("out").password("hash").build();

        pelada = Pelada.builder()
                .id(10L)
                .name("Pelada")
                .dayOfWeek("FRIDAY")
                .timeOfDay("18:00")
                .duration(2f)
                .members(new HashSet<>(Set.of(admin, player)))
                .admins(new HashSet<>(Set.of(admin)))
                .build();
        MembershipStubs.stubMembership(peladaRepository, pelada);
    }

    @Test
    void getRanking_withRankingRecord_returnsCorrectValues() {
        Ranking ranking = Ranking.builder()
                .id(1L)
                .pelada(pelada)
                .user(admin)
                .goals(10)
                .assists(5)
                .matchesPlayed(20)
                .wins(12)
                .build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(rankingRepository.findByPeladaWithUser(pelada)).thenReturn(List.of(ranking));

        List<RankingDTO> result = rankingService.getRanking(10L, "admin@example.com");

        assertThat(result).isNotEmpty();
        RankingDTO adminRank = result.stream()
                .filter(r -> r.userId().equals(1L))
                .findFirst().orElseThrow();
        assertThat(adminRank.goals()).isEqualTo(10);
        assertThat(adminRank.assists()).isEqualTo(5);
    }

    @Test
    void getRanking_noRankingRecord_returnsDefaultValues() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(rankingRepository.findByPeladaWithUser(pelada)).thenReturn(Collections.emptyList());

        List<RankingDTO> result = rankingService.getRanking(10L, "admin@example.com");

        assertThat(result).hasSize(2); // both members included
        result.forEach(r -> {
            assertThat(r.goals()).isEqualTo(0);
            assertThat(r.assists()).isEqualTo(0);
        });
    }

    @Test
    void getRanking_sortedByGoalsThenAssists() {
        Ranking r1 = Ranking.builder().id(1L).pelada(pelada).user(admin).goals(5).assists(3).build();
        Ranking r2 = Ranking.builder().id(2L).pelada(pelada).user(player).goals(5).assists(8).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(rankingRepository.findByPeladaWithUser(pelada)).thenReturn(List.of(r1, r2));

        List<RankingDTO> result = rankingService.getRanking(10L, "admin@example.com");

        // Both have 5 goals, player has more assists so should be first
        assertThat(result.get(0).userId()).isEqualTo(2L);
        assertThat(result.get(1).userId()).isEqualTo(1L);
    }

    @Test
    void getRanking_allMembersIncluded() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(rankingRepository.findByPeladaWithUser(pelada)).thenReturn(Collections.emptyList());

        List<RankingDTO> result = rankingService.getRanking(10L, "admin@example.com");

        // pelada has 2 members: admin and player
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(RankingDTO::userId))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void getRanking_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> rankingService.getRanking(10L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getRanking_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankingService.getRanking(999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getRanking_callerNotMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> rankingService.getRanking(10L, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getRanking_tieInGoals_sortedByAssists() {
        Ranking r1 = Ranking.builder().id(1L).pelada(pelada).user(admin).goals(3).assists(2).build();
        Ranking r2 = Ranking.builder().id(2L).pelada(pelada).user(player).goals(3).assists(5).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(rankingRepository.findByPeladaWithUser(pelada)).thenReturn(List.of(r1, r2));

        List<RankingDTO> result = rankingService.getRanking(10L, "admin@example.com");

        assertThat(result.get(0).assists()).isEqualTo(5);
        assertThat(result.get(1).assists()).isEqualTo(2);
    }

    // ── getPlayerPeladaStats tests ──────────────────────────────────────────────

    @Test
    void getPlayerPeladaStats_withRankingAndAwards_returnsCorrectValues() {
        Ranking ranking = Ranking.builder().id(1L).pelada(pelada).user(player).goals(7).assists(3).matchesPlayed(10).wins(4).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(2L)).thenReturn(Optional.of(player));
        when(rankingRepository.findByPeladaAndUser(pelada, player)).thenReturn(Optional.of(ranking));
        when(userDailyStatsRepository.sumMatchWinsByUserAndPelada(player, pelada)).thenReturn(6);
        when(dailyAwardRepository.countAwardsByUserAndPelada(2L, 10L)).thenReturn(awardRows(1, 1, 0, 0));

        PlayerPeladaStatsDTO result = rankingService.getPlayerPeladaStats(10L, 2L, "admin@example.com");

        assertThat(result.userId()).isEqualTo(2L);
        assertThat(result.goals()).isEqualTo(7);
        assertThat(result.assists()).isEqualTo(3);
        assertThat(result.matchesPlayed()).isEqualTo(10);
        assertThat(result.wins()).isEqualTo(4);
        assertThat(result.matchWins()).isEqualTo(6);
        assertThat(result.artilheiroWins()).isEqualTo(1);
        assertThat(result.garcomWins()).isEqualTo(1);
        assertThat(result.puskasWins()).isEqualTo(0);
        assertThat(result.bolaMurchaWins()).isEqualTo(0);
    }

    @Test
    void getPlayerPeladaStats_noRankingRecord_returnsZeroStats() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(2L)).thenReturn(Optional.of(player));
        when(rankingRepository.findByPeladaAndUser(pelada, player)).thenReturn(Optional.empty());
        when(userDailyStatsRepository.sumMatchWinsByUserAndPelada(player, pelada)).thenReturn(0);
        when(dailyAwardRepository.countAwardsByUserAndPelada(2L, 10L)).thenReturn(awardRows(0, 0, 0, 0));

        PlayerPeladaStatsDTO result = rankingService.getPlayerPeladaStats(10L, 2L, "admin@example.com");

        assertThat(result.goals()).isEqualTo(0);
        assertThat(result.assists()).isEqualTo(0);
        assertThat(result.matchesPlayed()).isEqualTo(0);
        assertThat(result.wins()).isEqualTo(0);
        assertThat(result.matchWins()).isEqualTo(0);
        assertThat(result.artilheiroWins()).isEqualTo(0);
        assertThat(result.garcomWins()).isEqualTo(0);
        assertThat(result.puskasWins()).isEqualTo(0);
        assertThat(result.bolaMurchaWins()).isEqualTo(0);
    }

    @Test
    void getPlayerPeladaStats_countsMultipleAwardWins() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(2L)).thenReturn(Optional.of(player));
        when(rankingRepository.findByPeladaAndUser(pelada, player)).thenReturn(Optional.empty());
        when(userDailyStatsRepository.sumMatchWinsByUserAndPelada(player, pelada)).thenReturn(0);
        when(dailyAwardRepository.countAwardsByUserAndPelada(2L, 10L)).thenReturn(awardRows(0, 0, 2, 1));

        PlayerPeladaStatsDTO result = rankingService.getPlayerPeladaStats(10L, 2L, "admin@example.com");

        assertThat(result.puskasWins()).isEqualTo(2);
        assertThat(result.bolaMurchaWins()).isEqualTo(1);
    }

    @Test
    void getPlayerPeladaStats_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> rankingService.getPlayerPeladaStats(10L, 2L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPlayerPeladaStats_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankingService.getPlayerPeladaStats(999L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPlayerPeladaStats_callerNotMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> rankingService.getPlayerPeladaStats(10L, 2L, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getPlayerPeladaStats_targetUserNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankingService.getPlayerPeladaStats(10L, 999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPlayerPeladaStats_targetNotMember_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> rankingService.getPlayerPeladaStats(10L, 3L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(rankingRepository, dailyAwardRepository, userDailyStatsRepository);
    }

    // ── getPlayerPeladaHistory tests ────────────────────────────────────────────

    @Test
    void getPlayerPeladaHistory_mapsRowsInRepositoryOrder() {
        Daily newer = Daily.builder().id(21L).pelada(pelada).dailyDate(LocalDate.of(2026, 9, 26)).build();
        Daily older = Daily.builder().id(20L).pelada(pelada).dailyDate(LocalDate.of(2026, 9, 19)).build();
        UserDailyStats newerStats = UserDailyStats.builder().id(2L).daily(newer).user(player)
                .goals(2).assists(1).matchesPlayed(4).wins(3).wonSession(true).build();
        UserDailyStats olderStats = UserDailyStats.builder().id(1L).daily(older).user(player)
                .goals(0).assists(2).matchesPlayed(4).wins(1).wonSession(false).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userDailyStatsRepository.findHistoryByUserAndPelada(2L, 10L, Pageable.unpaged())).thenReturn(List.of(newerStats, olderStats));

        PlayerPeladaHistoryDTO result = rankingService.getPlayerPeladaHistory(10L, 2L, null, "admin@example.com");

        assertThat(result.userId()).isEqualTo(2L);
        assertThat(result.totalSessions()).isEqualTo(2);
        assertThat(result.rows()).hasSize(2);
        PlayerPeladaHistoryDTO.Row first = result.rows().get(0);
        assertThat(first.dailyId()).isEqualTo(21L);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 9, 26));
        assertThat(first.goals()).isEqualTo(2);
        assertThat(first.assists()).isEqualTo(1);
        assertThat(first.matchesPlayed()).isEqualTo(4);
        assertThat(first.wins()).isEqualTo(3);
        assertThat(first.wonSession()).isTrue();
        assertThat(result.rows().get(1).dailyId()).isEqualTo(20L);
        assertThat(result.rows().get(1).wonSession()).isFalse();
    }

    @Test
    void getPlayerPeladaHistory_noSessions_returnsEmptyRows() {
        when(userAuthHelper.getAuthenticatedUser("player@example.com")).thenReturn(player);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userDailyStatsRepository.findHistoryByUserAndPelada(1L, 10L, PageRequest.of(0, 5))).thenReturn(List.of());

        PlayerPeladaHistoryDTO result = rankingService.getPlayerPeladaHistory(10L, 1L, 5, "player@example.com");

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.totalSessions()).isZero();
        assertThat(result.rows()).isEmpty();
        verify(userDailyStatsRepository, never()).countHistoryByUserAndPelada(anyLong(), anyLong());
    }

    @Test
    void getPlayerPeladaHistory_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankingService.getPlayerPeladaHistory(999L, 2L, null, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPlayerPeladaHistory_callerNotMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> rankingService.getPlayerPeladaHistory(10L, 2L, null, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(userDailyStatsRepository);
    }

    @Test
    void getPlayerPeladaHistory_targetNotMember_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> rankingService.getPlayerPeladaHistory(10L, 3L, null, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(userDailyStatsRepository);
    }

    @Test
    void getPlayerPeladaHistory_limitReached_countsAllSessions() {
        Daily d1 = Daily.builder().id(31L).pelada(pelada).dailyDate(LocalDate.of(2026, 9, 26)).build();
        Daily d2 = Daily.builder().id(30L).pelada(pelada).dailyDate(LocalDate.of(2026, 9, 19)).build();
        UserDailyStats s1 = UserDailyStats.builder().id(11L).daily(d1).user(player).build();
        UserDailyStats s2 = UserDailyStats.builder().id(10L).daily(d2).user(player).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userDailyStatsRepository.findHistoryByUserAndPelada(2L, 10L, PageRequest.of(0, 2))).thenReturn(List.of(s1, s2));
        when(userDailyStatsRepository.countHistoryByUserAndPelada(2L, 10L)).thenReturn(9L);

        PlayerPeladaHistoryDTO result = rankingService.getPlayerPeladaHistory(10L, 2L, 2, "admin@example.com");

        assertThat(result.rows()).extracting(PlayerPeladaHistoryDTO.Row::dailyId).containsExactly(31L, 30L);
        assertThat(result.totalSessions()).isEqualTo(9);
    }

    @Test
    void getPlayerPeladaHistory_limitOutOfRange_throwsBadRequest() {
        for (int limit : new int[]{0, RankingService.MAX_HISTORY_LIMIT + 1}) {
            assertThatThrownBy(() -> rankingService.getPlayerPeladaHistory(10L, 2L, limit, "admin@example.com"))
                    .isInstanceOf(AppException.class)
                    .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(peladaRepository, userDailyStatsRepository);
    }

    private static List<Object[]> awardRows(int artilheiro, int garcom, int puskas, int bolaMurcha) {
        return List.of(
                new Object[]{"ARTILHEIRO", (long) artilheiro},
                new Object[]{"GARCOM", (long) garcom},
                new Object[]{"PUSKAS", (long) puskas},
                new Object[]{"BOLA_MURCHA", (long) bolaMurcha});
    }
}
