package com.futspring.backend.domain.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// A browser's PushSubscription.toJSON() (expirationTime is ignored). The owner comes from the token
public record PushSubscriptionRequestDTO(
        @NotBlank(message = "Informe o endpoint")
        @Size(max = 1024, message = "Endpoint longo demais") String endpoint,
        @NotNull(message = "Informe as chaves") @Valid Keys keys) {

    public record Keys(
            @NotBlank(message = "Informe a chave p256dh")
            @Size(max = 200, message = "Chave p256dh longa demais") String p256dh,
            @NotBlank(message = "Informe a chave auth")
            @Size(max = 50, message = "Chave auth longa demais") String auth) {
    }
}
