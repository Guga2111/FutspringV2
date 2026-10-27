package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetMensalistaRequestDTO {

    @NotNull(message = "Informe se o jogador é mensalista")
    private Boolean isMensalista;
}
