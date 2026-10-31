package com.futspring.backend.domain.daily.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;

@Builder
@JsonDeserialize(builder = MatchResultDTO.MatchResultDTOBuilder.class)
public record MatchResultDTO(
        // null creates a new match; otherwise it must be a match of the route's daily
        Long matchId,

        @NotNull(message = "Informe o time 1")
        Long team1Id,

        @NotNull(message = "Informe o time 2")
        Long team2Id,

        @Min(value = 0, message = "O placar não pode ser negativo")
        int team1Score,

        @Min(value = 0, message = "O placar não pode ser negativo")
        int team2Score,

        List<@Valid PlayerStatInputDTO> playerStats) {

    @Builder
    @JsonDeserialize(builder = PlayerStatInputDTO.PlayerStatInputDTOBuilder.class)
    public record PlayerStatInputDTO(
            @NotNull(message = "Informe o jogador")
            Long userId,

            @Min(value = 0, message = "Gols não podem ser negativos")
            int goals,

            @Min(value = 0, message = "Assistências não podem ser negativas")
            int assists) {

        // Keep omitted primitive inputs at zero; Jackson still rejects explicit nulls.
        @JsonPOJOBuilder(withPrefix = "")
        public static class PlayerStatInputDTOBuilder {
        }
    }

    // Keep omitted primitive inputs at zero; Jackson still rejects explicit nulls.
    @JsonPOJOBuilder(withPrefix = "")
    public static class MatchResultDTOBuilder {
    }
}
