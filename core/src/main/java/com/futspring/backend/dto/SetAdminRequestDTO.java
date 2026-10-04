package com.futspring.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetAdminRequestDTO {

    @NotNull(message = "Informe se o jogador é administrador")
    private Boolean isAdmin;
}
