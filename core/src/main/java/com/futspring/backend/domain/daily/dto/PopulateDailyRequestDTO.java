package com.futspring.backend.domain.daily.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;

public record PopulateDailyRequestDTO(
        @NotEmpty(message = "Informe pelo menos um time")
        List<@Valid ParsedTeamDTO> teams,

        List<@Valid ParsedMatchDTO> matches) {

    public record ParsedTeamDTO(
            @NotBlank(message = "Informe a cor do time")
            String colorName,

            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida")
            String colorHex,

            @NotNull(message = "Informe os jogadores do time")
            List<@Valid ParsedPlayerDTO> players) {
    }

    @Builder
    @JsonDeserialize(builder = ParsedPlayerDTO.ParsedPlayerDTOBuilder.class)
    public record ParsedPlayerDTO(
            @NotNull(message = "Informe o jogador")
            Long userId,

            @Min(value = 0, message = "Gols não podem ser negativos")
            int totalGoals,

            @Min(value = 0, message = "Assistências não podem ser negativas")
            int totalAssists) {

        // Keep omitted primitive inputs at zero; Jackson still rejects explicit nulls.
        @JsonPOJOBuilder(withPrefix = "")
        public static class ParsedPlayerDTOBuilder {
        }
    }

    @Builder
    @JsonDeserialize(builder = ParsedMatchDTO.ParsedMatchDTOBuilder.class)
    public record ParsedMatchDTO(
            @NotBlank(message = "Informe o time 1")
            String team1ColorName,

            @Min(value = 0, message = "O placar não pode ser negativo")
            int team1Score,

            @NotBlank(message = "Informe o time 2")
            String team2ColorName,

            @Min(value = 0, message = "O placar não pode ser negativo")
            int team2Score) {

        // Keep omitted primitive inputs at zero; Jackson still rejects explicit nulls.
        @JsonPOJOBuilder(withPrefix = "")
        public static class ParsedMatchDTOBuilder {
        }
    }
}
