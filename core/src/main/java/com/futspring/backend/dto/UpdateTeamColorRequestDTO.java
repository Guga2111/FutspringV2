package com.futspring.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTeamColorRequestDTO {

    @NotNull(message = "A cor é obrigatória")
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "A cor deve ser um hexadecimal de 6 dígitos (ex.: #3b82f6)")
    private String color;
}
