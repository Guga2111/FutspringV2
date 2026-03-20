package com.futspring.backend.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResultDTO {

    private Long matchId;
    private Long team1Id;
    private Long team2Id;

    @Min(0)
    private int team1Score;

    @Min(0)
    private int team2Score;

    private List<PlayerStatInputDTO> playerStats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerStatInputDTO {
        private Long userId;

        @Min(0)
        private int goals;

        @Min(0)
        private int assists;
    }
}
