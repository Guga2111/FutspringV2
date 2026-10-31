package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;

public record BulkSetStarsRequestDTO(
        @NotEmpty(message = "Selecione pelo menos um jogador")
        @Size(max = 100, message = "Selecione no máximo 100 jogadores")
        @UniqueElements(message = "Jogador repetido na seleção")
        List<@NotNull(message = "Informe o jogador") Long> userIds,

        @NotNull(message = "Informe as estrelas")
        @Min(value = 1, message = "Mínimo de 1 estrela")
        @Max(value = 5, message = "Máximo de 5 estrelas")
        Integer stars) {
}
