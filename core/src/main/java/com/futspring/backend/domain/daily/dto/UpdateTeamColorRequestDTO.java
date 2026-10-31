package com.futspring.backend.domain.daily.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateTeamColorRequestDTO(
        @NotNull(message = "A cor é obrigatória")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "A cor deve ser um hexadecimal de 6 dígitos (ex.: #3b82f6)")
        String color) {
}
