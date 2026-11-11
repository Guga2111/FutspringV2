package com.futspring.backend.domain.auth.dto;

import com.futspring.backend.domain.auth.BcryptLength;
import com.futspring.backend.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank(message = "O nome de usuário é obrigatório")
        @Size(min = 3, max = 30, message = "O nome de usuário deve ter entre 3 e 30 caracteres")
        String username,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres")
        @BcryptLength
        String password,

        // the version of the terms of use and privacy policy the user accepted (LegalTerms.CURRENT_VERSION)
        @NotBlank(message = "Aceite os termos de uso para criar a conta")
        @Size(max = 20, message = "Versão dos termos inválida")
        String termsVersion) {

    public RegisterRequestDTO {
        email = User.normalizeEmail(email);
    }
}
