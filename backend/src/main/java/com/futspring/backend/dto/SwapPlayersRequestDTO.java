package com.futspring.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SwapPlayersRequestDTO {
    private Long player1Id;
    private Long player2Id;
}
