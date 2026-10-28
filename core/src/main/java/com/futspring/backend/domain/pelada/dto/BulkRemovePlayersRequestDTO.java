package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRemovePlayersRequestDTO {

    @NotEmpty(message = "Selecione pelo menos um jogador")
    @Size(max = 100, message = "Selecione no máximo 100 jogadores")
    @UniqueElements(message = "Jogador repetido na seleção")
    private List<@NotNull(message = "Informe o jogador") Long> userIds;
}
