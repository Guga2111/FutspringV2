package com.futspring.backend.domain.daily.dto;

import jakarta.validation.constraints.NotNull;

public record SwapPlayersRequestDTO(
        @NotNull(message = "Informe o primeiro jogador")
        Long player1Id,

        @NotNull(message = "Informe o segundo jogador")
        Long player2Id) {
}
