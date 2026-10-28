package com.futspring.backend.domain.notification;

import com.futspring.backend.shared.config.JwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class UnsubscribeTokensTest {

    private static UnsubscribeTokens tokens(String secret) {
        JwtConfig config = new JwtConfig();
        ReflectionTestUtils.setField(config, "secret", secret);
        return new UnsubscribeTokens(config);
    }

    private final UnsubscribeTokens tokens = tokens("test-secret-key-that-is-at-least-32-characters-long-for-hmac");

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void roundTrip() {
        String token = tokens.create(42L, NotificationCategory.ATTENDANCE);

        assertThat(token).doesNotContain("=", "+", "/");
        assertThat(tokens.verify(token)).contains(new UnsubscribeTokens.Target(42L, NotificationCategory.ATTENDANCE));
    }

    @Test
    void swappedCategoryOrUser_isRejected() {
        String signature = tokens.create(42L, NotificationCategory.ATTENDANCE).split("\\.")[1];

        assertThat(tokens.verify(encode("42:SESSION") + "." + signature)).isEmpty();
        assertThat(tokens.verify(encode("43:ATTENDANCE") + "." + signature)).isEmpty();
    }

    @Test
    void tamperedSignature_isRejected() {
        String token = tokens.create(42L, NotificationCategory.MEMBERSHIP);
        char last = token.charAt(token.length() - 1);

        assertThat(tokens.verify(token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A'))).isEmpty();
    }

    @Test
    void anotherSecret_isRejected() {
        String token = tokens("another-secret-key-that-is-at-least-32-characters-long").create(42L, NotificationCategory.AWARDS);

        assertThat(tokens.verify(token)).isEmpty();
    }

    @Test
    void garbage_isRejected() {
        assertThat(tokens.verify(null)).isEmpty();
        assertThat(tokens.verify("")).isEmpty();
        assertThat(tokens.verify("abc")).isEmpty();
        assertThat(tokens.verify("a.b.c")).isEmpty();
        assertThat(tokens.verify("%%%.###")).isEmpty();
    }

    // A validly signed payload that isn't userId:CATEGORY can't come from create, but must still fail cleanly
    @Test
    void signedButMalformedPayload_isRejected() {
        String token = tokens.create(42L, NotificationCategory.SESSION);
        assertThat(tokens.verify(token.replace(token.split("\\.")[0], encode("x")))).isEmpty();
    }
}
