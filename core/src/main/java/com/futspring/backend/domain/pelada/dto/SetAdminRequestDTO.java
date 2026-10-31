package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.NotNull;

public record SetAdminRequestDTO(
        @NotNull(message = "Informe se o jogador é administrador")
        Boolean isAdmin) {
}
