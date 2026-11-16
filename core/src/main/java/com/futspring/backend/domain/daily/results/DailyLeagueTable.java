package com.futspring.backend.domain.daily.results;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.team.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The session's league table, computed from its matches: shown live while the session runs and kept as the final
 * table once it is finalized. Runs inside the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class DailyLeagueTable {

    private final LeagueTableEntryRepository leagueTableEntryRepository;

    /** Replaces the session's table with the one computed from these matches; returns the entries by position. */
    public List<LeagueTableEntry> replace(Daily daily, List<Team> teams, List<Match> matches) {
        delete(daily);

        Map<Long, LeagueTableEntry> leagueMap = new HashMap<>();
        for (Team team : teams) {
            leagueMap.put(team.getId(), LeagueTableEntry.builder()
                    .daily(daily)
                    .team(team)
                    .build());
        }

        for (Match match : matches) {
            Long t1Id = match.getTeam1().getId();
            Long t2Id = match.getTeam2().getId();
            int s1 = match.getTeam1Score() != null ? match.getTeam1Score() : 0;
            int s2 = match.getTeam2Score() != null ? match.getTeam2Score() : 0;

            LeagueTableEntry e1 = leagueMap.get(t1Id);
            LeagueTableEntry e2 = leagueMap.get(t2Id);

            if (e1 != null) { e1.setGoalsFor(e1.getGoalsFor() + s1); e1.setGoalsAgainst(e1.getGoalsAgainst() + s2); }
            if (e2 != null) { e2.setGoalsFor(e2.getGoalsFor() + s2); e2.setGoalsAgainst(e2.getGoalsAgainst() + s1); }

            if (s1 > s2) {
                if (e1 != null) { e1.setWins(e1.getWins() + 1); e1.setPoints(e1.getPoints() + 3); }
                if (e2 != null) { e2.setLosses(e2.getLosses() + 1); }
            } else if (s2 > s1) {
                if (e2 != null) { e2.setWins(e2.getWins() + 1); e2.setPoints(e2.getPoints() + 3); }
                if (e1 != null) { e1.setLosses(e1.getLosses() + 1); }
            } else {
                if (e1 != null) { e1.setDraws(e1.getDraws() + 1); e1.setPoints(e1.getPoints() + 1); }
                if (e2 != null) { e2.setDraws(e2.getDraws() + 1); e2.setPoints(e2.getPoints() + 1); }
            }
        }

        List<LeagueTableEntry> sortedEntries = leagueMap.values().stream()
                .sorted(Comparator
                        .comparingInt(LeagueTableEntry::getPoints).reversed()
                        .thenComparingInt((LeagueTableEntry e) -> e.getGoalsAgainst() - e.getGoalsFor()))
                .collect(Collectors.toList());

        for (int i = 0; i < sortedEntries.size(); i++) {
            sortedEntries.get(i).setPosition(i + 1);
        }
        leagueTableEntryRepository.saveAll(sortedEntries);
        return sortedEntries;
    }

    public void delete(Daily daily) {
        leagueTableEntryRepository.deleteAll(leagueTableEntryRepository.findByDailyOrderByPositionAsc(daily));
    }
}
