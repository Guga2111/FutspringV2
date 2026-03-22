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
public class UserStatsTimelineDTO {

    private List<TimelinePoint> points;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimelinePoint {
        private LocalDate date;
        private int goals;
        private int assists;
        private int wins;
        private int matchesPlayed;
    }
}
