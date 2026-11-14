package com.futspring.backend.domain.telemetry.dto;

import com.futspring.backend.domain.telemetry.ClientErrorSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// An uncaught error of the web app (client/src/lib/errorReporting.ts); path without query or hash
public record ClientErrorRequestDTO(
        @NotBlank(message = "Informe a mensagem") @Size(max = 500, message = "Mensagem longa demais") String message,
        @Size(max = 4000, message = "Stack longo demais") String stack,
        @NotBlank(message = "Informe a página") @Size(max = 200, message = "Página longa demais") String path,
        @Size(max = 40, message = "Versão longa demais") String release,
        @Size(max = 300, message = "User agent longo demais") String userAgent,
        ClientErrorSource source) {
}
