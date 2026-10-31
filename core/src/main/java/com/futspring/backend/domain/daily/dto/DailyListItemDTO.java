package com.futspring.backend.domain.daily.dto;

import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

// Built by DailyListItemAssembler (counts come from grouped queries, isConfirmed is the caller's attendance)
@Builder
public record DailyListItemDTO(
        Long id,
        LocalDate dailyDate,
        String dailyTime,
        DailyStatus status,
        int confirmedPlayerCount,
        @JsonProperty("isFinished")
        boolean isFinished,
        int teamCount,
        int matchCount,
        String championImage,
        @JsonProperty("isConfirmed")
        boolean isConfirmed,
        // numberOfTeams × playersPerTeam
        int capacity,
        // session start, in the pelada's zone
        Instant startsAt,
        // from when the caller may confirm themselves; null for mensalistas (no window). The server re-checks it
        Instant confirmOpensAt) {
}
