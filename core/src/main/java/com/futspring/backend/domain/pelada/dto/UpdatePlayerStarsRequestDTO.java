package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdatePlayerStarsRequestDTO(
        @NotNull(message = "Informe as estrelas")
        @Min(value = 1, message = "Mínimo de 1 estrela")
        @Max(value = 5, message = "Máximo de 5 estrelas")
        Integer stars) {
}
