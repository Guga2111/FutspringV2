package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.entity.UserDailyStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

    // --- findByUserOrderByDailyDailyDateDesc ---

    @Test
    void findByUserOrderByDailyDailyDateDesc_correctOrder() {
        List<UserDailyStats> result = userDailyStatsRepository.findByUserOrderByDailyDailyDateDesc(player);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDaily().getDailyDate()).isEqualTo(LocalDate.of(2024, 3, 8));
        assertThat(result.get(2).getDaily().getDailyDate()).isEqualTo(LocalDate.of(2024, 1, 5));
    }

    // --- Global aggregate queries ---

    @Test
    void sumGoalsByUser_returnsCorrectSum() {
        int sum = userDailyStatsRepository.sumGoalsByUser(player);
        assertThat(sum).isEqualTo(6); // 3 + 1 + 2
    }

    @Test
    void sumAssistsByUser_returnsCorrectSum() {
        int sum = userDailyStatsRepository.sumAssistsByUser(player);
        assertThat(sum).isEqualTo(3); // 2 + 0 + 1
    }

    @Test
    void sumMatchesPlayedByUser_returnsCorrectSum() {
        int sum = userDailyStatsRepository.sumMatchesPlayedByUser(player);
        assertThat(sum).isEqualTo(9); // 4 + 3 + 2
    }

    @Test
    void sumMatchWinsByUser_returnsCorrectSum() {
        int sum = userDailyStatsRepository.sumMatchWinsByUser(player);
        assertThat(sum).isEqualTo(5); // 2 + 1 + 2
    }

    @Test
    void countSessionsByUser_returnsCorrectCount() {
        long count = userDailyStatsRepository.countSessionsByUser(player);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void countSessionWinsByUser_returnsCorrectCount() {
        long count = userDailyStatsRepository.countSessionWinsByUser(player);
        assertThat(count).isEqualTo(2); // daily1 and daily3 have wonSession=true
    }

    // --- Pelada-scoped aggregates ---

    @Test
    void sumGoalsByUserAndPelada_returnsCorrectSum() {
        int sum = userDailyStatsRepository.sumGoalsByUserAndPelada(player, pelada);
        assertThat(sum).isEqualTo(6);
    }

    @Test
    void sumAssistsByUserAndPelada_returnsCorrectSum() {
        int sum = userDailyStatsRepository.sumAssistsByUserAndPelada(player, pelada);
        assertThat(sum).isEqualTo(3);
    }

    @Test
    void sumMatchesPlayedByUserAndPelada_returnsCorrectSum() {
        int sum = userDailyStatsRepository.sumMatchesPlayedByUserAndPelada(player, pelada);
        assertThat(sum).isEqualTo(9);
    }

    @Test
    void countSessionWinsByUserAndPelada_returnsCorrectCount() {
        long count = userDailyStatsRepository.countSessionWinsByUserAndPelada(player, pelada);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void aggregatesForUserWithNoStats_returnZero() {
        User newPlayer = userRepository.save(User.builder()
                .email("new@example.com").username("new").password("hash").build());

        assertThat(userDailyStatsRepository.sumGoalsByUser(newPlayer)).isEqualTo(0);
        assertThat(userDailyStatsRepository.sumAssistsByUser(newPlayer)).isEqualTo(0);
        assertThat(userDailyStatsRepository.countSessionsByUser(newPlayer)).isEqualTo(0);
    }
}
