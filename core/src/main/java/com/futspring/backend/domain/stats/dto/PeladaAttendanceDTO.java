package com.futspring.backend.domain.stats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record PeladaAttendanceDTO(
        // Inclusive period of the ranking
        LocalDate from,
        LocalDate to,
        // Finalized sessions of the pelada in the period (the denominator of every rate)
        long totalSessions,
        // One row per current member, best rate first
        List<Row> rows) {

    @Builder
    public record Row(
            Long userId,
            String username,
            String userImage,
            long sessionsAttended,
            // sessionsAttended / totalSessions, 0 when there are no sessions
            double rate,
            // the member's current type, not their type at each session of the period
            @JsonProperty("isMensalista")
            boolean isMensalista) {
    }
}
