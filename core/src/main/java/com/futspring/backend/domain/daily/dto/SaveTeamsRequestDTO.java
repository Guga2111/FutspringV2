package com.futspring.backend.domain.daily.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Every team of the session, built by hand; replaces the current teams. */
public record SaveTeamsRequestDTO(
        @NotNull(message = "Informe os times")
        @Size(min = 2, max = 10, message = "Monte de 2 a 10 times")
        List<@Valid @NotNull TeamInput> teams) {

    public record TeamInput(
            @NotBlank(message = "O nome do time é obrigatório")
            @Size(max = 30, message = "Máximo de 30 caracteres")
            String name,

            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "A cor deve ser um hexadecimal de 6 dígitos (ex.: #3b82f6)")
            String color,

            @NotNull(message = "Informe os jogadores do time")
            List<@NotNull @Positive Long> playerIds) {
    }
}
