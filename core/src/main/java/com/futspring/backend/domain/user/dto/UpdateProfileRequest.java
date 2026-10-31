package com.futspring.backend.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Partial update: null fields are left unchanged; an empty position clears it
public record UpdateProfileRequest(
        @Size(min = 3, max = 30, message = "O nome de usuário deve ter entre 3 e 30 caracteres")
        String username,

        @Pattern(regexp = "^(|GOALKEEPER|DEFENDER|MIDFIELDER|FORWARD|GOLEIRO|ZAGUEIRO|MEIO|ATACANTE)$",
                message = "Posição inválida")
        String position) {
}
