package com.futspring.backend.domain.user.dto;

import com.futspring.backend.domain.auth.BcryptLength;
import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequestDTO(
        @NotBlank(message = "Informe a sua senha")
        @BcryptLength
        String password) {
}
