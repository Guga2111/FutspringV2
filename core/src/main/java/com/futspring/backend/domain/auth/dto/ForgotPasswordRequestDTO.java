package com.futspring.backend.domain.auth.dto;

import com.futspring.backend.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequestDTO(
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email) {

    public ForgotPasswordRequestDTO {
        email = User.normalizeEmail(email);
    }
}
