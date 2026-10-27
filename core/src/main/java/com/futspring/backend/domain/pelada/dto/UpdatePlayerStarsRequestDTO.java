package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlayerStarsRequestDTO {

    @NotNull(message = "Informe as estrelas")
    @Min(value = 1, message = "Mínimo de 1 estrela")
    @Max(value = 5, message = "Máximo de 5 estrelas")
    private Integer stars;
}
