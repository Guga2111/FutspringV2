package com.futspring.backend.domain.telemetry;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ClientErrorRedactionTest {
    @Test void removesCredentialsInPathsQueriesHeadersJsonAndStacks() {
        for (String raw : java.util.List.of(
                "/join/secret-value", "/api/v1/invite-links/secret-value/join", "/join%2Fsecret-value",
                "https://app/reset-password?token=secret-value", "Bearer secret-value", "{\"token\":\"secret-value\"}",
                "https://app/verify-email?token=secret-value:12:8", "password=secret-value")) {
            assertThat(ClientErrorRedaction.redact(raw)).doesNotContain("secret-value");
        }
    }
    @Test void preservesUsefulDiagnosticsAndHandlesMissingFields() {
        assertThat(ClientErrorRedaction.redact(null)).isNull();
        assertThat(ClientErrorRedaction.redact("TypeError at https://app/assets/app.js:12:8")).isEqualTo("TypeError at https://app/assets/app.js:12:8");
    }
}
