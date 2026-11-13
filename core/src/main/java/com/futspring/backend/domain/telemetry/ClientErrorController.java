package com.futspring.backend.domain.telemetry;

import com.futspring.backend.domain.telemetry.dto.ClientErrorRequestDTO;
import com.futspring.backend.shared.config.SecurityConfig;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Public: an error with an expired session must still arrive (JwtAuthFilter ignores the header here)
@RestController
@RequiredArgsConstructor
public class ClientErrorController {

    private final ClientErrorService clientErrorService;

    @PostMapping(SecurityConfig.CLIENT_ERRORS_PATH)
    public ResponseEntity<Void> report(@Valid @RequestBody ClientErrorRequestDTO request) {
        clientErrorService.record(request);
        return ResponseEntity.noContent().build();
    }
}
