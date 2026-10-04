package com.futspring.backend.domain.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Partial update: null fields are left unchanged; an empty position clears it
@Data
public class UpdateProfileRequest {

    @Size(min = 3, max = 30, message = "O nome de usuário deve ter entre 3 e 30 caracteres")
    private String username;

    @Pattern(regexp = "^(|GOALKEEPER|DEFENDER|MIDFIELDER|FORWARD|GOLEIRO|ZAGUEIRO|MEIO|ATACANTE)$",
            message = "Posição inválida")
    private String position;

    @Min(value = 1, message = "Mínimo de 1 estrela")
    @Max(value = 5, message = "Máximo de 5 estrelas")
    private Integer stars;
}
