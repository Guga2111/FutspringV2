package com.futspring.backend.service;

import com.futspring.backend.dto.StatsDTO;
import com.futspring.backend.dto.UserMatchHistoryDTO;
import com.futspring.backend.dto.UserStatsTimelineDTO;
import com.futspring.backend.entity.User;
import com.futspring.backend.entity.UserDailyStats;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.DailyAwardRepository;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.StatsRepository;
import com.futspring.backend.repository.UserDailyStatsRepository;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Profile statistics. The owner sees everything; another user can see the profile only if they share a
 * pelada, and then the timeline and history only include sessions of the peladas they share.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final PeladaRepository peladaRepository;
    private final StatsRepository statsRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;
    private final DailyAwardRepository dailyAwardRepository;
    private final UserAuthenticationHelper userAuthHelper;

    @Transactional(readOnly = true)
    public StatsDTO getStats(Long userId, String callerEmail) {
        User user = findUser(userId);
        requireCanView(user, userAuthHelper.getAuthenticatedUser(callerEmail));

        StatsDTO dto = statsRepository.findByUser(user)
                .map(StatsDTO::fromStats)
                .orElseGet(() -> StatsDTO.fromUser(user));
        Map<String, Integer> awards = dailyAwardRepository.countAwardsByUser(userId).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).intValue()));
        dto.setWiltballWins(awards.getOrDefault("BOLA_MURCHA", 0));
        dto.setArtilheiroWins(awards.getOrDefault("ARTILHEIRO", 0));
        dto.setGarcomWins(awards.getOrDefault("GARCOM", 0));
        return dto;
    }

    @Transactional(readOnly = true)
    public UserStatsTimelineDTO getTimeline(Long userId, LocalDate from, LocalDate to, String callerEmail) {
        if (from.isAfter(to)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A data inicial deve ser anterior à data final");
        }
        User user = findUser(userId);
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        requireCanView(user, caller);

        List<UserDailyStats> statsList = isSelf(user, caller)
                ? userDailyStatsRepository.findByUserAndDateRange(user, from, to)
                : userDailyStatsRepository.findByUserAndDateRangeInPeladas(user, from, to,
                        peladaRepository.findIdsByMemberId(caller.getId()));

        List<UserStatsTimelineDTO.TimelinePoint> points = statsList.stream()
                .map(uds -> UserStatsTimelineDTO.TimelinePoint.builder()
                        .date(uds.getDaily().getDailyDate())
                        .goals(uds.getGoals())
                        .assists(uds.getAssists())
                        .wins(uds.getWins())
                        .matchesPlayed(uds.getMatchesPlayed())
                        .build())
                .toList();

        return UserStatsTimelineDTO.builder().points(points).build();
    }

    @Transactional(readOnly = true)
    public UserMatchHistoryDTO getMatchHistory(Long userId, String callerEmail) {
        User user = findUser(userId);
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        requireCanView(user, caller);

        List<UserDailyStats> statsList = isSelf(user, caller)
                ? userDailyStatsRepository.findHistoryByUser(user)
                : userDailyStatsRepository.findHistoryByUserInPeladas(user,
                        peladaRepository.findIdsByMemberId(caller.getId()));

        List<UserMatchHistoryDTO.MatchRow> rows = statsList.stream()
                .map(uds -> UserMatchHistoryDTO.MatchRow.builder()
                        .dailyId(uds.getDaily().getId())
                        .date(uds.getDaily().getDailyDate())
                        .peladaId(uds.getDaily().getPelada().getId())
                        .peladaName(uds.getDaily().getPelada().getName())
                        .goals(uds.getGoals())
                        .assists(uds.getAssists())
                        .matchesPlayed(uds.getMatchesPlayed())
                        .wins(uds.getWins())
                        .result(uds.getMatchesPlayed() == 0 ? "—" : uds.isWonSession() ? "W" : "L")
                        .build())
                .toList();

        return UserMatchHistoryDTO.builder().rows(rows).build();
    }

    private void requireCanView(User user, User caller) {
        if (!isSelf(user, caller) && !peladaRepository.existsSharedPelada(caller.getId(), user.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Você só pode ver estatísticas de jogadores das suas peladas");
        }
    }

    private static boolean isSelf(User user, User caller) {
        return user.getId().equals(caller.getId());
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }
}
