package com.futspring.backend.domain.stats.dto;

import com.futspring.backend.domain.stats.entity.Stats;
import com.futspring.backend.domain.user.User;
import lombok.Builder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Builder
public record StatsDTO(
        Long userId,
        String username,
        int goals,
        int assists,
        int matchesPlayed,
        int wins,
        int sessionsPlayed,
        int matchWins,
        int wiltballWins,
        int artilheiroWins,
        int garcomWins,
        List<LocalDate> puskasDates) {

    public static StatsDTO fromStats(Stats stats, int wiltballWins, int artilheiroWins, int garcomWins) {
        User user = stats.getUser();
        return StatsDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .goals(stats.getGoals())
                .assists(stats.getAssists())
                .matchesPlayed(stats.getMatchesPlayed())
                .wins(stats.getWins())
                .sessionsPlayed(stats.getSessionsPlayed())
                .matchWins(stats.getMatchWins())
                .wiltballWins(wiltballWins)
                .artilheiroWins(artilheiroWins)
                .garcomWins(garcomWins)
                .puskasDates(stats.getPuskasDates())
                .build();
    }

    public static StatsDTO fromUser(User user, int wiltballWins, int artilheiroWins, int garcomWins) {
        return StatsDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .goals(0)
                .assists(0)
                .matchesPlayed(0)
                .wins(0)
                .sessionsPlayed(0)
                .matchWins(0)
                .wiltballWins(wiltballWins)
                .artilheiroWins(artilheiroWins)
                .garcomWins(garcomWins)
                .puskasDates(new ArrayList<>())
                .build();
    }
}
