package com.futspring.backend.domain.stats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class PeladaAttendanceDTO {

    // Inclusive period of the ranking
    private LocalDate from;
    private LocalDate to;
    // Finalized sessions of the pelada in the period (the denominator of every rate)
    private long totalSessions;
    // One row per current member, best rate first
    private List<Row> rows;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Row {
        private Long userId;
        private String username;
        private String userImage;
        private long sessionsAttended;
        // sessionsAttended / totalSessions, 0 when there are no sessions
        private double rate;
        // the member's current type, not their type at each session of the period
        @JsonProperty("isMensalista")
        private boolean isMensalista;
    }
}
