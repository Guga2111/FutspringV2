package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.NotNull;

public record SetMensalistaRequestDTO(
        @NotNull(message = "Informe se o jogador é mensalista")
        Boolean isMensalista) {
}
