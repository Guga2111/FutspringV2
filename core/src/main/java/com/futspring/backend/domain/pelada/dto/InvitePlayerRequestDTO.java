package com.futspring.backend.domain.pelada.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InvitePlayerRequestDTO(@NotNull(message = "Informe o jogador") @Positive(message = "Jogador inválido") Long userId) { }
