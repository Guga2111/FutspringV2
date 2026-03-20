package com.futspring.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyDetailDTO {

    private Long id;
    private LocalDate dailyDate;
    private String dailyTime;
    private String status;
    private boolean isFinished;
    private String championImage;
    private List<PlayerDTO> confirmedPlayers;
    private List<TeamDTO> teams;
    private List<MatchDTO> matches;
    private List<UserDailyStatsDTO> playerStats;
    private List<LeagueTableEntryDTO> leagueTable;
    private AwardDTO award;
    private Long peladaId;
    private String peladaName;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerDTO {
        private Long id;
        private String username;
        private String image;
        private int stars;
        private String position;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TeamDTO {
        private Long id;
        private String name;
        private int totalStars;
        private List<PlayerDTO> players;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchDTO {
        private Long id;
        private Long team1Id;
        private String team1Name;
        private Long team2Id;
        private String team2Name;
        private Integer team1Score;
        private Integer team2Score;
        private Long winnerId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserDailyStatsDTO {
        private Long userId;
        private String username;
        private int goals;
        private int assists;
        private int matchesPlayed;
        private int wins;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LeagueTableEntryDTO {
        private Long teamId;
        private String teamName;
        private int position;
        private int wins;
        private int draws;
        private int losses;
        private int goalsFor;
        private int goalsAgainst;
        private int goalDiff;
        private int points;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AwardDTO {
        private Long puskasWinnerId;
        private String puskasWinnerName;
        private Long wiltballWinnerId;
        private String wiltballWinnerName;
    }
}
