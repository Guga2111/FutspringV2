package com.futspring.backend.domain.daily.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResultDTO {

    // null creates a new match; otherwise it must be a match of the route's daily
    private Long matchId;

    @NotNull(message = "Informe o time 1")
    private Long team1Id;

    @NotNull(message = "Informe o time 2")
    private Long team2Id;

    @Min(value = 0, message = "O placar não pode ser negativo")
    private int team1Score;

    @Min(value = 0, message = "O placar não pode ser negativo")
    private int team2Score;

    private List<@Valid PlayerStatInputDTO> playerStats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerStatInputDTO {

        @NotNull(message = "Informe o jogador")
        private Long userId;

        @Min(value = 0, message = "Gols não podem ser negativos")
        private int goals;

        @Min(value = 0, message = "Assistências não podem ser negativas")
        private int assists;
    }
}
