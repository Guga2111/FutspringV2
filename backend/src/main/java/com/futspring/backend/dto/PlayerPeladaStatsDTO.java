package com.futspring.backend.dto;

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
