package com.futspring.backend.domain.stats.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record PlayerPeladaHistoryDTO(
        Long userId,
        // All finalized sessions of the player in the pelada, regardless of the limit
        long totalSessions,
        // Newest first, at most `limit` rows when a limit is given
        List<Row> rows) {

    @Builder
    public record Row(
            Long dailyId,
            LocalDate date,
            int goals,
            int assists,
            int matchesPlayed,
            int wins,
            boolean wonSession) {
    }
}
