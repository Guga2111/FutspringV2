package com.futspring.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequestDTO(
        @NotBlank(message = "Informe a sua senha")
        String password) {
}
