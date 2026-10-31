package com.futspring.backend.domain.daily.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FinalizeDailyRequestDTO(
        // optional; null means no winners for the award
        List<@NotNull Long> puskasWinnerIds,

        List<@NotNull Long> wiltballWinnerIds) {
}
