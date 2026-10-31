package com.futspring.backend.domain.stats.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record UserStatsTimelineDTO(
        List<TimelinePoint> points) {

    @Builder
    public record TimelinePoint(
            LocalDate date,
            int goals,
            int assists,
            int wins,
            int matchesPlayed) {
    }
}
