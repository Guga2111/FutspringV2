package com.futspring.backend.domain.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerPeladaStatsDTO {

    private Long userId;
    // The player's stars in this pelada
    private int stars;
    private int goals;
    private int assists;
    private int matchesPlayed;
    private int wins;
    private int matchWins;
    private int artilheiroWins;
    private int garcomWins;
    private int puskasWins;
    private int bolaMurchaWins;
}
