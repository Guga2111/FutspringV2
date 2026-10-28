package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferCreatorRequestDTO {

    @NotNull(message = "Informe o novo criador")
    private Long userId;
}
