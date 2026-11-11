package com.futspring.backend.domain.user.dto;

import com.futspring.backend.domain.auth.BcryptLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequestDTO(
        @NotBlank(message = "Informe a senha atual")
        @BcryptLength
        String currentPassword,

        // same rule as RegisterRequestDTO.password
        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres")
        @BcryptLength
        String newPassword) {
}
