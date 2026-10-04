package com.futspring.backend.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "A mensagem não pode ser vazia")
    @Size(max = 500, message = "A mensagem deve ter no máximo 500 caracteres")
    private String content;
}
