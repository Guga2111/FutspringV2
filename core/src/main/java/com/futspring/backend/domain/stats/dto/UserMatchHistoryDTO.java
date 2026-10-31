package com.futspring.backend.domain.stats.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record UserMatchHistoryDTO(
        List<MatchRow> rows) {

    @Builder
    public record MatchRow(
            Long dailyId,
            LocalDate date,
            Long peladaId,
            String peladaName,
            int goals,
            int assists,
            int matchesPlayed,
            int wins,
            String result) {
    }
}
