package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.NotNull;

public record AddPlayerRequestDTO(
        @NotNull(message = "Informe o jogador")
        Long userId) {
}
