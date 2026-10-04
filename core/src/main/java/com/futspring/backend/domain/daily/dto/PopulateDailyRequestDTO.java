package com.futspring.backend.domain.daily.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class PopulateDailyRequestDTO {

    @NotEmpty(message = "Informe pelo menos um time")
    private List<@Valid ParsedTeamDTO> teams;

    private List<@Valid ParsedMatchDTO> matches;

    @Data
    public static class ParsedTeamDTO {
        @NotBlank(message = "Informe a cor do time")
        private String colorName;

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida")
        private String colorHex;

        @NotNull(message = "Informe os jogadores do time")
        private List<@Valid ParsedPlayerDTO> players;
    }

    @Data
    public static class ParsedPlayerDTO {
        @NotNull(message = "Informe o jogador")
        private Long userId;

        @Min(value = 0, message = "Gols não podem ser negativos")
        private int totalGoals;

        @Min(value = 0, message = "Assistências não podem ser negativas")
        private int totalAssists;
    }

    @Data
    public static class ParsedMatchDTO {
        @NotBlank(message = "Informe o time 1")
        private String team1ColorName;

        @Min(value = 0, message = "O placar não pode ser negativo")
        private int team1Score;

        @NotBlank(message = "Informe o time 2")
        private String team2ColorName;

        @Min(value = 0, message = "O placar não pode ser negativo")
        private int team2Score;
    }
}
