package com.futspring.backend.domain.auth.dto;

import com.futspring.backend.domain.auth.BcryptLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequestDTO(
        @NotBlank(message = "Informe o token de verificação") @Size(max = 200, message = "Token de verificação inválido") String token,
        @NotBlank(message = "Informe a senha da sua conta") @BcryptLength String password) { }
