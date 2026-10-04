package com.futspring.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SwapPlayersRequestDTO {

    @NotNull(message = "Informe o primeiro jogador")
    private Long player1Id;

    @NotNull(message = "Informe o segundo jogador")
    private Long player2Id;
}
