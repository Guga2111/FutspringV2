package com.futspring.backend.service;

import com.futspring.backend.dto.StatsDTO;
import com.futspring.backend.dto.UserStatsTimelineDTO;
import com.futspring.backend.entity.User;
import com.futspring.backend.entity.UserDailyStats;
import com.futspring.backend.exception.AppException;
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

    @Transactional(readOnly = true)
    public StatsDTO getStats(Long userId, String callerEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        return statsRepository.findByUser(user)
                .map(StatsDTO::fromStats)
                .orElse(StatsDTO.fromUser(user));
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
}
