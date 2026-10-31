package com.futspring.backend.domain.stats.dto;

import lombok.Builder;

@Builder
public record PlayerPeladaStatsDTO(
        Long userId,
        // The player's stars in this pelada
        int stars,
        int goals,
        int assists,
        int matchesPlayed,
        int wins,
        int matchWins,
        int artilheiroWins,
        int garcomWins,
        int puskasWins,
        int bolaMurchaWins) {
}
