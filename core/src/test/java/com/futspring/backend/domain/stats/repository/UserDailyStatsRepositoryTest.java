package com.futspring.backend.domain.stats.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.stats.entity.UserDailyStats;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@TestPropertySource(locations = "classpath:application.properties")
class UserDailyStatsRepositoryTest {

    @Autowired
    UserDailyStatsRepository userDailyStatsRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    DailyRepository dailyRepository;
    @Autowired
    PeladaRepository peladaRepository;

    User player;
    Pelada pelada;
    Daily daily1;
    Daily daily2;
    Daily daily3;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(User.builder()
                .email("creator@example.com").username("creator").password("hash").build());

        player = userRepository.save(User.builder()
                .email("player@example.com").username("player").password("hash").build());

        pelada = peladaRepository.save(Pelada.builder()
                .name("Pelada").dayOfWeek("FRIDAY").timeOfDay("18:00").duration(2f)
                .creator(creator).build());

        daily1 = dailyRepository.save(Daily.builder()
                .pelada(pelada).dailyDate(LocalDate.of(2024, 1, 5)).dailyTime("18:00").build());
        daily2 = dailyRepository.save(Daily.builder()
                .pelada(pelada).dailyDate(LocalDate.of(2024, 2, 9)).dailyTime("18:00").build());
        daily3 = dailyRepository.save(Daily.builder()
                .pelada(pelada).dailyDate(LocalDate.of(2024, 3, 8)).dailyTime("18:00").build());

        userDailyStatsRepository.save(UserDailyStats.builder()
                .daily(daily1).user(player).goals(3).assists(2).matchesPlayed(4).wins(2).wonSession(true).build());
        userDailyStatsRepository.save(UserDailyStats.builder()
                .daily(daily2).user(player).goals(1).assists(0).matchesPlayed(3).wins(1).wonSession(false).build());
        userDailyStatsRepository.save(UserDailyStats.builder()
                .daily(daily3).user(player).goals(2).assists(1).matchesPlayed(2).wins(2).wonSession(true).build());
    }

    // --- Date range query ---

    @Test
    void findByUserAndDateRange_withinRange_returnsCorrectEntries() {
        List<UserDailyStats> result = userDailyStatsRepository.findByUserAndDateRange(
                player, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 28));

        assertThat(result).hasSize(2);
    }

    @Test
    void findByUserAndDateRange_boundsInclusive() {
        List<UserDailyStats> result = userDailyStatsRepository.findByUserAndDateRange(
                player, LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 5));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGoals()).isEqualTo(3);
    }

    @Test
    void findByUserAndDateRange_emptyRange_returnsEmpty() {
        List<UserDailyStats> result = userDailyStatsRepository.findByUserAndDateRange(
                player, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertThat(result).isEmpty();
    }

    // --- findHistoryByUser / findHistoryByUserInPeladas ---

    @Test
    void findHistoryByUser_correctOrder() {
        List<UserDailyStats> result = userDailyStatsRepository.findHistoryByUser(player);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDaily().getDailyDate()).isEqualTo(LocalDate.of(2024, 3, 8));
        assertThat(result.get(2).getDaily().getDailyDate()).isEqualTo(LocalDate.of(2024, 1, 5));
    }

    @Test
    void findHistoryByUserInPeladas_otherPelada_returnsEmpty() {
        assertThat(userDailyStatsRepository.findHistoryByUserInPeladas(player, List.of(-1L))).isEmpty();
        assertThat(userDailyStatsRepository.findHistoryByUserInPeladas(player, List.of(pelada.getId()))).hasSize(3);
    }

    // --- Batch aggregates used by AggregateRebuildService ---

    @Test
    void aggregateStatsByUsers_returnsSums() {
        List<Object[]> rows = userDailyStatsRepository.aggregateStatsByUsers(List.of(player));

        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        assertThat(row[0]).isEqualTo(player.getId());
        assertThat(((Number) row[1]).intValue()).isEqualTo(6); // goals 3 + 1 + 2
        assertThat(((Number) row[2]).intValue()).isEqualTo(3); // assists 2 + 0 + 1
        assertThat(((Number) row[3]).intValue()).isEqualTo(9); // matches 4 + 3 + 2
        assertThat(((Number) row[4]).intValue()).isEqualTo(5); // match wins 2 + 1 + 2
        assertThat(((Number) row[5]).intValue()).isEqualTo(3); // sessions
        assertThat(((Number) row[6]).intValue()).isEqualTo(2); // session wins
    }

    @Test
    void aggregateRankingByUsersAndPelada_returnsSums() {
        List<Object[]> rows = userDailyStatsRepository.aggregateRankingByUsersAndPelada(List.of(player), pelada);

        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0)[1]).intValue()).isEqualTo(6);
        assertThat(((Number) rows.get(0)[4]).intValue()).isEqualTo(2);
    }

    @Test
    void sumMatchWinsByUserAndPelada_returnsCorrectSum() {
        assertThat(userDailyStatsRepository.sumMatchWinsByUserAndPelada(player, pelada)).isEqualTo(5);
    }

    // --- findHistoryByUserAndPelada ---

    @Test
    void findHistoryByUserAndPelada_onlyThatPelada_newestFirst() {
        User creator = userRepository.save(User.builder()
                .email("other-creator@example.com").username("other").password("hash").build());
        Pelada otherPelada = peladaRepository.save(Pelada.builder()
                .name("Outra").dayOfWeek("MONDAY").timeOfDay("20:00").duration(1f)
                .creator(creator).build());
        Daily otherDaily = dailyRepository.save(Daily.builder()
                .pelada(otherPelada).dailyDate(LocalDate.of(2024, 4, 1)).dailyTime("20:00").build());
        userDailyStatsRepository.save(UserDailyStats.builder()
                .daily(otherDaily).user(player).goals(9).build());

        List<UserDailyStats> result = userDailyStatsRepository
                .findHistoryByUserAndPelada(player.getId(), pelada.getId(), Pageable.unpaged());

        assertThat(result).hasSize(3);
        assertThat(result).extracting(uds -> uds.getDaily().getId())
                .containsExactly(daily3.getId(), daily2.getId(), daily1.getId());
        assertThat(userDailyStatsRepository.countHistoryByUserAndPelada(player.getId(), pelada.getId()))
                .isEqualTo(3);
    }

    @Test
    void findHistoryByUserAndPelada_withLimit_returnsMostRecent() {
        List<UserDailyStats> result = userDailyStatsRepository
                .findHistoryByUserAndPelada(player.getId(), pelada.getId(), PageRequest.of(0, 2));

        assertThat(result).extracting(uds -> uds.getDaily().getId())
                .containsExactly(daily3.getId(), daily2.getId());
    }

    @Test
    void findHistoryByUserAndPelada_otherUser_returnsEmpty() {
        User newPlayer = userRepository.save(User.builder()
                .email("nohistory@example.com").username("nohistory").password("hash").build());

        assertThat(userDailyStatsRepository.findHistoryByUserAndPelada(newPlayer.getId(), pelada.getId(), Pageable.unpaged()))
                .isEmpty();
    }

    @Test
    void aggregatesForUserWithNoStats_returnNoRows() {
        User newPlayer = userRepository.save(User.builder()
                .email("new@example.com").username("new").password("hash").build());

        assertThat(userDailyStatsRepository.aggregateStatsByUsers(List.of(newPlayer))).isEmpty();
        assertThat(userDailyStatsRepository.sumMatchWinsByUserAndPelada(newPlayer, pelada)).isEqualTo(0);
    }
}
