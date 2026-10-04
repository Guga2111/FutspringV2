package com.futspring.backend.service;

import com.futspring.backend.dto.PlayerPeladaHistoryDTO;
import com.futspring.backend.dto.PlayerPeladaStatsDTO;
import com.futspring.backend.dto.RankingDTO;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.Ranking;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.PeladaAccessHelper;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.DailyAwardRepository;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.RankingRepository;
import com.futspring.backend.repository.UserDailyStatsRepository;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class RankingService {

    private final PeladaRepository peladaRepository;
    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final RankingRepository rankingRepository;
    private final DailyAwardRepository dailyAwardRepository;
    private final UserRepository userRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;

    @Transactional(readOnly = true)
    public List<RankingDTO> getRanking(Long peladaId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireMember(pelada, caller);

        // Rankings with their users in one query; members without a ranking row show zeros
        Map<Long, Ranking> rankingByUserId = rankingRepository.findByPeladaWithUser(pelada).stream()
                .collect(Collectors.toMap(r -> r.getUser().getId(), r -> r));

        return pelada.getMembers().stream()
                .map(member -> {
                    Ranking ranking = rankingByUserId.get(member.getId());
                    return ranking != null ? RankingDTO.fromRanking(ranking) : RankingDTO.fromUser(member);
                })
                .sorted(Comparator.comparingInt(RankingDTO::getGoals).reversed()
                        .thenComparing(Comparator.comparingInt(RankingDTO::getAssists).reversed())
                        .thenComparing(RankingDTO::getUsername, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PlayerPeladaStatsDTO getPlayerPeladaStats(Long peladaId, Long userId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireMember(pelada, caller);

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Jogador não encontrado nesta pelada"));
        if (!accessHelper.isMember(pelada, targetUser)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Jogador não encontrado nesta pelada");
        }

        Ranking ranking = rankingRepository.findByPeladaAndUser(pelada, targetUser).orElse(null);
        // The four award counts in one query
        Map<String, Integer> awards = dailyAwardRepository.countAwardsByUserAndPelada(userId, peladaId).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).intValue()));

        return PlayerPeladaStatsDTO.builder()
                .userId(userId)
                .goals(ranking != null ? ranking.getGoals() : 0)
                .assists(ranking != null ? ranking.getAssists() : 0)
                .matchesPlayed(ranking != null ? ranking.getMatchesPlayed() : 0)
                .wins(ranking != null ? ranking.getWins() : 0)
                .matchWins(userDailyStatsRepository.sumMatchWinsByUserAndPelada(targetUser, pelada))
                .artilheiroWins(awards.getOrDefault("ARTILHEIRO", 0))
                .garcomWins(awards.getOrDefault("GARCOM", 0))
                .puskasWins(awards.getOrDefault("PUSKAS", 0))
                .bolaMurchaWins(awards.getOrDefault("BOLA_MURCHA", 0))
                .build();
    }

    public static final int MAX_HISTORY_LIMIT = 100;

    // limit = last N sessions (1..MAX_HISTORY_LIMIT); null = every session
    @Transactional(readOnly = true)
    public PlayerPeladaHistoryDTO getPlayerPeladaHistory(Long peladaId, Long userId, Integer limit, String callerEmail) {
        if (limit != null && (limit < 1 || limit > MAX_HISTORY_LIMIT)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "O limite deve estar entre 1 e " + MAX_HISTORY_LIMIT);
        }

        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);

        Pelada pelada = findPelada(peladaId);
        accessHelper.requireMember(pelada, caller);

        if (!peladaRepository.existsByIdAndMembers_Id(peladaId, userId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Jogador não encontrado nesta pelada");
        }

        Pageable pageable = limit != null ? PageRequest.of(0, limit) : Pageable.unpaged();
        List<PlayerPeladaHistoryDTO.Row> rows = userDailyStatsRepository
                .findHistoryByUserAndPelada(userId, peladaId, pageable).stream()
                .map(uds -> PlayerPeladaHistoryDTO.Row.builder()
                        .dailyId(uds.getDaily().getId())
                        .date(uds.getDaily().getDailyDate())
                        .goals(uds.getGoals())
                        .assists(uds.getAssists())
                        .matchesPlayed(uds.getMatchesPlayed())
                        .wins(uds.getWins())
                        .wonSession(uds.isWonSession())
                        .build())
                .collect(Collectors.toList());

        long totalSessions = limit != null && rows.size() == limit
                ? userDailyStatsRepository.countHistoryByUserAndPelada(userId, peladaId)
                : rows.size();

        return PlayerPeladaHistoryDTO.builder()
                .userId(userId)
                .totalSessions(totalSessions)
                .rows(rows)
                .build();
    }

    private Pelada findPelada(Long id) {
        return peladaRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
    }
}
