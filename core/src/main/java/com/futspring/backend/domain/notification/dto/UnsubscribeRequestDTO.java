package com.futspring.backend.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;

// The token of an e-mail's "Desativar" link (/notifications/unsubscribe?token=…), posted by the public page
public record UnsubscribeRequestDTO(@NotBlank(message = "Informe o token") String token) {
}
