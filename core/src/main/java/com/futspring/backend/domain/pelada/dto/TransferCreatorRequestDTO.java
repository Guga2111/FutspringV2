package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.NotNull;

public record TransferCreatorRequestDTO(
        @NotNull(message = "Informe o novo criador")
        Long userId) {
}
