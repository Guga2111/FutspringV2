package com.futspring.backend.domain.stats;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.stats.entity.Ranking;
import com.futspring.backend.domain.stats.entity.Stats;
import com.futspring.backend.domain.stats.repository.DailyAwardRepository;
import com.futspring.backend.domain.stats.repository.RankingRepository;
import com.futspring.backend.domain.stats.repository.StatsRepository;
import com.futspring.backend.domain.stats.repository.UserDailyStatsRepository;
import com.futspring.backend.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rebuilds the denormalized Ranking (per pelada) and Stats (global) rows of the given players from
 * UserDailyStats, with batch queries. Called after finalize, daily delete and pelada delete.
 */
@Service
@RequiredArgsConstructor
public class AggregateRebuildService {

    private final UserDailyStatsRepository userDailyStatsRepository;
    private final DailyAwardRepository dailyAwardRepository;
    private final RankingRepository rankingRepository;
    private final StatsRepository statsRepository;

    /** Rebuilds Ranking for {@code pelada} (skipped when null) and global Stats for {@code players}. */
    public void rebuild(Pelada pelada, Collection<User> players) {
        if (players.isEmpty()) {
            return;
        }
        List<User> playerList = new ArrayList<>(new LinkedHashSet<>(players));
        if (pelada != null) {
            rebuildRanking(pelada, playerList);
        }
        rebuildStats(playerList);
    }

    private void rebuildRanking(Pelada pelada, List<User> players) {
        Map<Long, Ranking> rankingMap = rankingRepository.findByPeladaAndUserIn(pelada, players).stream()
                .collect(Collectors.toMap(r -> r.getUser().getId(), r -> r));
        Map<Long, Object[]> agg = userDailyStatsRepository.aggregateRankingByUsersAndPelada(players, pelada).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        List<Ranking> toSave = new ArrayList<>();
        for (User player : players) {
            Ranking ranking = rankingMap.getOrDefault(player.getId(),
                    Ranking.builder().pelada(pelada).user(player).build());
            Object[] row = agg.get(player.getId());
            ranking.setGoals(intAt(row, 1));
            ranking.setAssists(intAt(row, 2));
            ranking.setMatchesPlayed(intAt(row, 3));
            ranking.setWins(intAt(row, 4));
            toSave.add(ranking);
        }
        rankingRepository.saveAll(toSave);
    }

    private void rebuildStats(List<User> players) {
        Map<Long, Stats> statsMap = statsRepository.findByUserIn(players).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));
        Map<Long, Object[]> agg = userDailyStatsRepository.aggregateStatsByUsers(players).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        Map<Long, List<LocalDate>> puskasByUser = new HashMap<>();
        dailyAwardRepository.findPuskasDatesByUsers(players).forEach(row ->
                puskasByUser.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((LocalDate) row[1]));

        List<Stats> toSave = new ArrayList<>();
        for (User player : players) {
            Stats stats = statsMap.getOrDefault(player.getId(), Stats.builder().user(player).build());
            Object[] row = agg.get(player.getId());
            stats.setGoals(intAt(row, 1));
            stats.setAssists(intAt(row, 2));
            stats.setMatchesPlayed(intAt(row, 3));
            stats.setMatchWins(intAt(row, 4));
            stats.setSessionsPlayed(intAt(row, 5));
            stats.setWins(intAt(row, 6));
            stats.setPuskasDates(new ArrayList<>(puskasByUser.getOrDefault(player.getId(), List.of())));
            toSave.add(stats);
        }
        statsRepository.saveAll(toSave);
    }

    private static int intAt(Object[] row, int index) {
        return row != null && row[index] != null ? ((Number) row[index]).intValue() : 0;
    }
}
