package com.futspring.backend.domain.notification.preference;

import com.futspring.backend.domain.notification.NotificationCategory;
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
        return new UnsubscribeTokens(config, java.time.Clock.systemUTC());
    }

    private final UnsubscribeTokens tokens = tokens("test-secret-key-that-is-at-least-32-characters-long-for-hmac");

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void roundTrip() {
        String token = tokens.create(42L, NotificationCategory.ATTENDANCE, 0);

        assertThat(token).doesNotContain("=", "+", "/");
        assertThat(tokens.verify(token)).contains(new UnsubscribeTokens.Target(42L, NotificationCategory.ATTENDANCE, 0));
    }

    @Test
    void swappedCategoryOrUser_isRejected() {
        String signature = tokens.create(42L, NotificationCategory.ATTENDANCE, 0).split("\\.")[1];

        assertThat(tokens.verify(encode("42:SESSION") + "." + signature)).isEmpty();
        assertThat(tokens.verify(encode("43:ATTENDANCE") + "." + signature)).isEmpty();
    }

    @Test
    void tamperedSignature_isRejected() {
        String token = tokens.create(42L, NotificationCategory.MEMBERSHIP, 0);
        char last = token.charAt(token.length() - 1);

        assertThat(tokens.verify(token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A'))).isEmpty();
    }

    @Test
    void anotherSecret_isRejected() {
        String token = tokens("another-secret-key-that-is-at-least-32-characters-long").create(42L, NotificationCategory.AWARDS, 0);

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
        String token = tokens.create(42L, NotificationCategory.SESSION, 0);
        assertThat(tokens.verify(token.replace(token.split("\\.")[0], encode("x")))).isEmpty();
    }

    @Test void expiresAtThirtyDaysAndRejectsPermanentLegacyTokens() {
        var config = new JwtConfig();
        ReflectionTestUtils.setField(config, "secret", "test-secret-key-that-is-at-least-32-characters-long-for-hmac");
        var start = java.time.Clock.fixed(java.time.Instant.parse("2026-11-13T00:00:00Z"),java.time.ZoneOffset.UTC);
        var issuer = new UnsubscribeTokens(config,start);
        String token = issuer.create(42L,NotificationCategory.ATTENDANCE,7);
        assertThat(issuer.verify(token)).contains(new UnsubscribeTokens.Target(42L,NotificationCategory.ATTENDANCE,7));
        assertThat(new UnsubscribeTokens(config,java.time.Clock.offset(start,java.time.Duration.ofDays(30))).verify(token)).isEmpty();
        assertThat(issuer.verify(encode("42:ATTENDANCE")+"."+token.split("\\.")[1])).isEmpty();
    }
}
