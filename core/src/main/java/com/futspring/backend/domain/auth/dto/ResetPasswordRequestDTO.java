package com.futspring.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequestDTO {

    @NotBlank(message = "O link de redefinição é obrigatório")
    private String token;

    // same rule as RegisterRequestDTO.password
    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres")
    private String newPassword;
}
