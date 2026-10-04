package com.futspring.backend.dto;

import com.futspring.backend.entity.Stats;
import com.futspring.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsDTO {

    private Long userId;
    private String username;
    private int goals;
    private int assists;
    private int matchesPlayed;
    private int wins;
    private int sessionsPlayed;
    private int matchWins;
    private int wiltballWins;
    private int artilheiroWins;
    private int garcomWins;
    private List<LocalDate> puskasDates;

    public static StatsDTO fromStats(Stats stats) {
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
                .wiltballWins(0)
                .artilheiroWins(0)
                .garcomWins(0)
                .puskasDates(stats.getPuskasDates())
                .build();
    }

    public static StatsDTO fromUser(User user) {
        return StatsDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .goals(0)
                .assists(0)
                .matchesPlayed(0)
                .wins(0)
                .sessionsPlayed(0)
                .matchWins(0)
                .wiltballWins(0)
                .artilheiroWins(0)
                .garcomWins(0)
                .puskasDates(new ArrayList<>())
                .build();
    }
}
