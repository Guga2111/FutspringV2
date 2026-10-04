package com.futspring.backend.service;

import com.futspring.backend.dto.StatsDTO;
import com.futspring.backend.dto.UserMatchHistoryDTO;
import com.futspring.backend.dto.UserStatsTimelineDTO;
import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.Stats;
import com.futspring.backend.entity.User;
import com.futspring.backend.entity.UserDailyStats;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.DailyAwardRepository;
import com.futspring.backend.repository.StatsRepository;
import com.futspring.backend.repository.UserDailyStatsRepository;
import com.futspring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    StatsRepository statsRepository;
    @Mock
    UserDailyStatsRepository userDailyStatsRepository;
    @Mock
    DailyAwardRepository dailyAwardRepository;

    StatsService statsService;

    User user;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(userRepository, statsRepository, userDailyStatsRepository, dailyAwardRepository);

        user = User.builder().id(1L).email("user@example.com").username("user").password("hash").build();
        pelada = Pelada.builder().id(10L).name("Pelada").dayOfWeek("FRIDAY").timeOfDay("18:00").duration(2f).build();
    }

    // --- getStats ---

    @Test
    void getStats_existingStats_returnsFromStatsEntity() {
        Stats stats = Stats.builder()
                .id(1L)
                .user(user)
                .goals(10)
                .assists(5)
                .matchesPlayed(20)
                .wins(8)
                .sessionsPlayed(6)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(statsRepository.findByUser(user)).thenReturn(Optional.of(stats));
        when(dailyAwardRepository.countByWiltballWinnersContaining(user)).thenReturn(2L);
        when(dailyAwardRepository.countByArtilheiroWinnersContaining(user)).thenReturn(3L);
        when(dailyAwardRepository.countByGarcomWinnersContaining(user)).thenReturn(1L);

        StatsDTO result = statsService.getStats(1L, "caller@example.com");

        assertThat(result.getGoals()).isEqualTo(10);
        assertThat(result.getAssists()).isEqualTo(5);
        assertThat(result.getWiltballWins()).isEqualTo(2);
        assertThat(result.getArtilheiroWins()).isEqualTo(3);
        assertThat(result.getGarcomWins()).isEqualTo(1);
    }

    @Test
    void getStats_noStatsEntity_returnsDefaultFromUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(statsRepository.findByUser(user)).thenReturn(Optional.empty());
        when(dailyAwardRepository.countByWiltballWinnersContaining(user)).thenReturn(0L);
        when(dailyAwardRepository.countByArtilheiroWinnersContaining(user)).thenReturn(0L);
        when(dailyAwardRepository.countByGarcomWinnersContaining(user)).thenReturn(0L);

        StatsDTO result = statsService.getStats(1L, "caller@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getGoals()).isEqualTo(0);
    }

    @Test
    void getStats_awardCountsPopulated() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(statsRepository.findByUser(user)).thenReturn(Optional.empty());
        when(dailyAwardRepository.countByWiltballWinnersContaining(user)).thenReturn(4L);
        when(dailyAwardRepository.countByArtilheiroWinnersContaining(user)).thenReturn(7L);
        when(dailyAwardRepository.countByGarcomWinnersContaining(user)).thenReturn(2L);

        StatsDTO result = statsService.getStats(1L, "caller@example.com");

        assertThat(result.getWiltballWins()).isEqualTo(4);
        assertThat(result.getArtilheiroWins()).isEqualTo(7);
        assertThat(result.getGarcomWins()).isEqualTo(2);
    }

    @Test
    void getStats_userNotFound_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statsService.getStats(99L, "caller@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- getTimeline ---

    @Test
    void getTimeline_success_returnsMappedPoints() {
        Daily daily = Daily.builder()
                .id(1L)
                .pelada(pelada)
                .dailyDate(LocalDate.of(2024, 1, 15))
                .dailyTime("18:00")
                .build();

        UserDailyStats uds = UserDailyStats.builder()
                .user(user)
                .daily(daily)
                .goals(3)
                .assists(2)
                .matchesPlayed(4)
                .wins(2)
                .build();

        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userDailyStatsRepository.findByUserAndDateRange(user, from, to)).thenReturn(List.of(uds));

        UserStatsTimelineDTO result = statsService.getTimeline(1L, from, to, "caller@example.com");

        assertThat(result.getPoints()).hasSize(1);
        assertThat(result.getPoints().get(0).getGoals()).isEqualTo(3);
        assertThat(result.getPoints().get(0).getDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void getTimeline_emptyRange_returnsEmptyList() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userDailyStatsRepository.findByUserAndDateRange(user, from, to)).thenReturn(Collections.emptyList());

        UserStatsTimelineDTO result = statsService.getTimeline(1L, from, to, "caller@example.com");

        assertThat(result.getPoints()).isEmpty();
    }

    @Test
    void getTimeline_fieldMapping_correctValues() {
        Daily daily = Daily.builder()
                .id(1L)
                .pelada(pelada)
                .dailyDate(LocalDate.of(2024, 3, 10))
                .dailyTime("19:00")
                .build();

        UserDailyStats uds = UserDailyStats.builder()
                .user(user)
                .daily(daily)
                .goals(5)
                .assists(3)
                .matchesPlayed(6)
                .wins(4)
                .build();

        LocalDate from = LocalDate.of(2024, 3, 1);
        LocalDate to = LocalDate.of(2024, 3, 31);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userDailyStatsRepository.findByUserAndDateRange(user, from, to)).thenReturn(List.of(uds));

        UserStatsTimelineDTO result = statsService.getTimeline(1L, from, to, "caller@example.com");

        UserStatsTimelineDTO.TimelinePoint point = result.getPoints().get(0);
        assertThat(point.getGoals()).isEqualTo(5);
        assertThat(point.getAssists()).isEqualTo(3);
        assertThat(point.getWins()).isEqualTo(4);
        assertThat(point.getMatchesPlayed()).isEqualTo(6);
    }

    @Test
    void getTimeline_userNotFound_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statsService.getTimeline(99L,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "caller@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- getMatchHistory ---

    @Test
    void getMatchHistory_success_returnsMappedRows() {
        Daily daily = Daily.builder()
                .id(1L)
                .pelada(pelada)
                .dailyDate(LocalDate.of(2024, 2, 10))
                .dailyTime("18:00")
                .build();

        UserDailyStats uds = UserDailyStats.builder()
                .user(user)
                .daily(daily)
                .goals(2)
                .assists(1)
                .matchesPlayed(3)
                .wins(2)
                .wonSession(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userDailyStatsRepository.findByUserOrderByDailyDailyDateDesc(user)).thenReturn(List.of(uds));

        UserMatchHistoryDTO result = statsService.getMatchHistory(1L, "caller@example.com");

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getResult()).isEqualTo("W");
    }

    @Test
    void getMatchHistory_lostSession_resultIsL() {
        Daily daily = Daily.builder()
                .id(1L)
                .pelada(pelada)
                .dailyDate(LocalDate.of(2024, 2, 10))
                .dailyTime("18:00")
                .build();

        UserDailyStats uds = UserDailyStats.builder()
                .user(user)
                .daily(daily)
                .goals(0)
                .assists(0)
                .matchesPlayed(2)
                .wins(0)
                .wonSession(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userDailyStatsRepository.findByUserOrderByDailyDailyDateDesc(user)).thenReturn(List.of(uds));

        UserMatchHistoryDTO result = statsService.getMatchHistory(1L, "caller@example.com");

        assertThat(result.getRows().get(0).getResult()).isEqualTo("L");
    }

    @Test
    void getMatchHistory_zeroMatchesPlayed_resultIsDash() {
        Daily daily = Daily.builder()
                .id(1L)
                .pelada(pelada)
                .dailyDate(LocalDate.of(2024, 2, 10))
                .dailyTime("18:00")
                .build();

        UserDailyStats uds = UserDailyStats.builder()
                .user(user)
                .daily(daily)
                .goals(0)
                .assists(0)
                .matchesPlayed(0)
                .wins(0)
                .wonSession(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userDailyStatsRepository.findByUserOrderByDailyDailyDateDesc(user)).thenReturn(List.of(uds));

        UserMatchHistoryDTO result = statsService.getMatchHistory(1L, "caller@example.com");

        assertThat(result.getRows().get(0).getResult()).isEqualTo("—");
    }

    @Test
    void getMatchHistory_fieldMapping_correct() {
        Daily daily = Daily.builder()
                .id(5L)
                .pelada(pelada)
                .dailyDate(LocalDate.of(2024, 5, 20))
                .dailyTime("20:00")
                .build();

        UserDailyStats uds = UserDailyStats.builder()
                .user(user)
                .daily(daily)
                .goals(4)
                .assists(3)
                .matchesPlayed(5)
                .wins(3)
                .wonSession(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userDailyStatsRepository.findByUserOrderByDailyDailyDateDesc(user)).thenReturn(List.of(uds));

        UserMatchHistoryDTO result = statsService.getMatchHistory(1L, "caller@example.com");

        UserMatchHistoryDTO.MatchRow row = result.getRows().get(0);
        assertThat(row.getDailyId()).isEqualTo(5L);
        assertThat(row.getPeladaId()).isEqualTo(10L);
        assertThat(row.getPeladaName()).isEqualTo("Pelada");
        assertThat(row.getGoals()).isEqualTo(4);
        assertThat(row.getAssists()).isEqualTo(3);
    }

    @Test
    void getMatchHistory_userNotFound_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statsService.getMatchHistory(99L, "caller@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
