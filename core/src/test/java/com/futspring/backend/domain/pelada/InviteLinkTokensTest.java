package com.futspring.backend.domain.pelada;

import com.futspring.backend.shared.config.JwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

class InviteLinkTokensTest {
    InviteLinkTokens tokens(String secret) {
        JwtConfig config = new JwtConfig(); ReflectionTestUtils.setField(config, "secret", secret);
        return new InviteLinkTokens(config);
    }
    @Test void deterministicSignedRecordIdRejectsTamperingAndAnotherKey() {
        var tokens = tokens("test-key-used-only-in-unit-tests"); String token = tokens.create(42L);
        assertThat(tokens.verify(token)).contains(42L); assertThat(tokens.create(42L)).isEqualTo(token);
        assertThat(tokens.verify("NDM" + token.substring(token.indexOf('.')))).isEmpty();
        assertThat(tokens.verify(token + "x")).isEmpty(); assertThat(tokens.verify(token + "=")).isEmpty();
        assertThat(tokens("different-test-key").verify(token)).isEmpty();
        assertThat(tokens.verify(null)).isEmpty(); assertThat(tokens.verify("invalid")).isEmpty();
        assertThat(tokens.verify("a.b.c")).isEmpty(); assertThat(tokens.verify(tokens.create(-1L))).isEmpty();
    }
}
