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
public class PlayerPeladaHistoryDTO {

    private Long userId;
    // All finalized sessions of the player in the pelada, regardless of the limit
    private long totalSessions;
    // Newest first, at most `limit` rows when a limit is given
    private List<Row> rows;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Row {
        private Long dailyId;
        private LocalDate date;
        private int goals;
        private int assists;
        private int matchesPlayed;
        private int wins;
        private boolean wonSession;
    }
}
