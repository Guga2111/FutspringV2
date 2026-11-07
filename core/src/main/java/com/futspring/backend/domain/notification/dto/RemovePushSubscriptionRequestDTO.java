package com.futspring.backend.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The endpoint of the caller's subscription to remove (logout, "Desativar neste aparelho")
public record RemovePushSubscriptionRequestDTO(
        @NotBlank(message = "Informe o endpoint")
        @Size(max = 1024, message = "Endpoint longo demais") String endpoint) {
}
