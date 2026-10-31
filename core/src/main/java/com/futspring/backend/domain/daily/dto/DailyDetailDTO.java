package com.futspring.backend.domain.daily.dto;

import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Builder
public record DailyDetailDTO(
        Long id,
        LocalDate dailyDate,
        String dailyTime,
        DailyStatus status,
        @JsonProperty("isFinished")
        boolean isFinished,
        String championImage,
        List<PlayerDTO> confirmedPlayers,
        List<TeamDTO> teams,
        List<MatchDTO> matches,
        List<UserDailyStatsDTO> playerStats,
        List<LeagueTableEntryDTO> leagueTable,
        AwardDTO award,
        Long peladaId,
        String peladaName,
        int numberOfTeams,
        int playersPerTeam,
        @JsonProperty("isAdmin")
        boolean isAdmin,
        List<PlayerDTO> peladaMembers,
        // numberOfTeams × playersPerTeam
        int capacity,
        // the pelada's IANA zone (the frontend shows the instants in it)
        String timeZone,
        Instant startsAt,
        // from when the caller may confirm themselves; null for mensalistas
        Instant confirmOpensAt,
        // the caller's bump in this session not dismissed yet, or null
        AttendanceBumpDTO myBump,
        // every bump of the session, newest first
        List<AttendanceBumpDTO> bumps) {

    @Builder
    public record PlayerDTO(
            Long id,
            String username,
            String image,
            int stars,
            String position,
            // only on confirmedPlayers and peladaMembers
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonProperty("isMensalista")
            Boolean isMensalista,
            // only on confirmedPlayers
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Instant confirmedAt) {
    }

    @Builder
    public record TeamDTO(
            Long id,
            String name,
            int totalStars,
            double averageStars,
            String color,
            List<PlayerDTO> players) {
    }

    @Builder
    public record PlayerStatDTO(
            Long userId,
            int goals,
            int assists) {
    }

    @Builder
    public record MatchDTO(
            Long id,
            Long team1Id,
            String team1Name,
            Long team2Id,
            String team2Name,
            Integer team1Score,
            Integer team2Score,
            Long winnerId,
            List<PlayerStatDTO> playerStats) {
    }

    @Builder
    public record UserDailyStatsDTO(
            Long userId,
            String username,
            int goals,
            int assists,
            int matchesPlayed,
            int wins) {
    }

    @Builder
    public record LeagueTableEntryDTO(
            Long teamId,
            String teamName,
            int position,
            int wins,
            int draws,
            int losses,
            int goalsFor,
            int goalsAgainst,
            int goalDiff,
            int points) {
    }

    @Builder
    public record AwardDTO(
            List<Long> puskasWinnerIds,
            List<String> puskasWinnerNames,
            List<Long> wiltballWinnerIds,
            List<String> wiltballWinnerNames,
            List<Long> artilheiroWinnerIds,
            List<String> artilheiroWinnerNames,
            List<Long> garcomWinnerIds,
            List<String> garcomWinnerNames) {
    }
}
