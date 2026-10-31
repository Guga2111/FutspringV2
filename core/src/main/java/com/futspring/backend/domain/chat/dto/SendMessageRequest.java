package com.futspring.backend.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "A mensagem não pode ser vazia")
        @Size(max = 500, message = "A mensagem deve ter no máximo 500 caracteres")
        String content) {
}
