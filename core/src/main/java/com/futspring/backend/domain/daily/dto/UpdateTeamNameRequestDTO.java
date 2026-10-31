package com.futspring.backend.domain.daily.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTeamNameRequestDTO(
        @NotBlank(message = "O nome do time é obrigatório")
        @Size(max = 30, message = "Máximo de 30 caracteres")
        String name) {
}
