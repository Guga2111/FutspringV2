package com.futspring.backend.service;

import com.futspring.backend.dto.StatsDTO;
import com.futspring.backend.dto.UserMatchHistoryDTO;
import com.futspring.backend.dto.UserStatsTimelineDTO;
import com.futspring.backend.entity.User;
import com.futspring.backend.entity.UserDailyStats;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.DailyAwardRepository;
import com.futspring.backend.repository.StatsRepository;
import com.futspring.backend.repository.UserDailyStatsRepository;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final StatsRepository statsRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;
    private final DailyAwardRepository dailyAwardRepository;

    @Transactional(readOnly = true)
    public StatsDTO getStats(Long userId, String callerEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        StatsDTO dto = statsRepository.findByUser(user)
                .map(StatsDTO::fromStats)
                .orElse(StatsDTO.fromUser(user));
        dto.setWiltballWins((int) dailyAwardRepository.countByWiltballWinnersContaining(user));
        dto.setArtilheiroWins((int) dailyAwardRepository.countByArtilheiroWinnersContaining(user));
        dto.setGarcomWins((int) dailyAwardRepository.countByGarcomWinnersContaining(user));
        return dto;
    }

    @Transactional(readOnly = true)
    public UserStatsTimelineDTO getTimeline(Long userId, LocalDate from, LocalDate to, String callerEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        List<UserDailyStats> statsList = userDailyStatsRepository.findByUserAndDateRange(user, from, to);

        List<UserStatsTimelineDTO.TimelinePoint> points = statsList.stream()
                .map(uds -> UserStatsTimelineDTO.TimelinePoint.builder()
                        .date(uds.getDaily().getDailyDate())
                        .goals(uds.getGoals())
                        .assists(uds.getAssists())
                        .wins(uds.getWins())
                        .matchesPlayed(uds.getMatchesPlayed())
                        .build())
                .collect(Collectors.toList());

        return UserStatsTimelineDTO.builder()
                .points(points)
                .build();
    }

    @Transactional(readOnly = true)
    public UserMatchHistoryDTO getMatchHistory(Long userId, String callerEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        List<UserDailyStats> statsList = userDailyStatsRepository.findByUserOrderByDailyDailyDateDesc(user);

        List<UserMatchHistoryDTO.MatchRow> rows = statsList.stream()
                .map(uds -> {
                    int wins = uds.getWins();
                    int matchesPlayed = uds.getMatchesPlayed();
                    String result;
                    if (matchesPlayed == 0) {
                        result = "—";
                    } else if (uds.isWonSession()) {
                        result = "W";
                    } else {
                        result = "L";
                    }
                    return UserMatchHistoryDTO.MatchRow.builder()
                            .dailyId(uds.getDaily().getId())
                            .date(uds.getDaily().getDailyDate())
                            .peladaId(uds.getDaily().getPelada().getId())
                            .peladaName(uds.getDaily().getPelada().getName())
                            .goals(uds.getGoals())
                            .assists(uds.getAssists())
                            .matchesPlayed(matchesPlayed)
                            .wins(wins)
                            .result(result)
                            .build();
                })
                .collect(Collectors.toList());

        return UserMatchHistoryDTO.builder()
                .rows(rows)
                .build();
    }
}
