package com.futspring.backend.domain.auth.dto;

import com.futspring.backend.domain.auth.BcryptLength;
import com.futspring.backend.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @BcryptLength
        String password) {

    public LoginRequestDTO {
        email = User.normalizeEmail(email);
    }
}
