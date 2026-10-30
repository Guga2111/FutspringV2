package com.futspring.backend.domain.auth;

import com.futspring.backend.shared.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtConfig jwtConfig = new JwtConfig();
        ReflectionTestUtils.setField(jwtConfig, "secret",
                "test-secret-key-that-is-at-least-32-characters-long");
        ReflectionTestUtils.setField(jwtConfig, "expirationMs", 3600000L);
        jwtService = new JwtService(jwtConfig);
    }

    // --- generateToken ---

    @Test
    void generateToken_returnsNonNull() {
        String token = jwtService.generateToken(1L, "user@example.com", 0);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void generateToken_subjectIsEmail() {
        String token = jwtService.generateToken(1L, "user@example.com", 0);
        assertThat(jwtService.extractAllClaims(token).getSubject()).isEqualTo("user@example.com");
    }

    @Test
    void generateToken_userIdClaimIsCorrect() {
        String token = jwtService.generateToken(77L, "user@example.com", 0);
        assertThat(jwtService.extractAllClaims(token).get("userId", Long.class)).isEqualTo(77L);
    }

    @Test
    void generateToken_emailClaimIsCorrect() {
        String token = jwtService.generateToken(1L, "test@domain.com", 0);
        Claims claims = jwtService.extractAllClaims(token);
        assertThat(claims.get("email", String.class)).isEqualTo("test@domain.com");
    }

    @Test
    void generateToken_isValidImmediately() {
        String token = jwtService.generateToken(1L, "user@example.com", 0);
        assertThat(jwtService.parseClaims(token)).isPresent();
    }

    // --- extractAllClaims ---

    @Test
    void extractAllClaims_validToken_returnsClaims() {
        String token = jwtService.generateToken(5L, "a@b.com", 0);
        Claims claims = jwtService.extractAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo("a@b.com");
    }

    @Test
    void extractAllClaims_tamperedToken_throwsJwtException() {
        String token = jwtService.generateToken(1L, "user@example.com", 0);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThatThrownBy(() -> jwtService.extractAllClaims(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractAllClaims_wrongKey_throwsJwtException() {
        JwtConfig otherConfig = new JwtConfig();
        ReflectionTestUtils.setField(otherConfig, "secret",
                "different-secret-key-that-is-at-least-32-characters-long");
        ReflectionTestUtils.setField(otherConfig, "expirationMs", 3600000L);
        JwtService otherService = new JwtService(otherConfig);

        String token = otherService.generateToken(1L, "user@example.com", 0);
        assertThatThrownBy(() -> jwtService.extractAllClaims(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractAllClaims_expiredToken_throwsJwtException() {
        JwtConfig expiredConfig = new JwtConfig();
        ReflectionTestUtils.setField(expiredConfig, "secret",
                "test-secret-key-that-is-at-least-32-characters-long");
        ReflectionTestUtils.setField(expiredConfig, "expirationMs", -1000L);
        JwtService expiredService = new JwtService(expiredConfig);

        String token = expiredService.generateToken(1L, "user@example.com", 0);
        assertThatThrownBy(() -> jwtService.extractAllClaims(token))
                .isInstanceOf(JwtException.class);
    }

    // --- parseClaims ---

    @Test
    void parseClaims_validToken_returnsClaims() {
        String token = jwtService.generateToken(1L, "user@example.com", 0);
        assertThat(jwtService.parseClaims(token)).isPresent();
    }

    @Test
    void parseClaims_expiredToken_returnsEmpty() {
        JwtConfig expiredConfig = new JwtConfig();
        ReflectionTestUtils.setField(expiredConfig, "secret",
                "test-secret-key-that-is-at-least-32-characters-long");
        ReflectionTestUtils.setField(expiredConfig, "expirationMs", -1000L);
        JwtService expiredService = new JwtService(expiredConfig);

        String token = expiredService.generateToken(1L, "user@example.com", 0);
        assertThat(jwtService.parseClaims(token)).isEmpty();
    }

    @Test
    void parseClaims_malformedToken_returnsEmpty() {
        assertThat(jwtService.parseClaims("not.a.valid.jwt")).isEmpty();
    }

    @Test
    void parseClaims_emptyString_returnsEmpty() {
        assertThat(jwtService.parseClaims("")).isEmpty();
    }

    // --- claims ---

    @Test
    void subject_returnsCorrectEmail() {
        String token = jwtService.generateToken(1L, "extract@test.com", 0);
        assertThat(jwtService.extractAllClaims(token).getSubject()).isEqualTo("extract@test.com");
    }

    @Test
    void userIdClaim_returnsCorrectId() {
        String token = jwtService.generateToken(123L, "user@example.com", 0);
        assertThat(jwtService.extractAllClaims(token).get("userId", Long.class)).isEqualTo(123L);
    }

    // --- tokens issued before the JJWT 0.11 → 0.13 upgrade (Boot 4) must keep working: no forced logout ---

    // Signed by the JJWT 0.11.5 code path (HS256, same test secret), expires in 2100
    private static final String LEGACY_TOKEN = "eyJhbGciOiJIUzI1NiJ9."
            + "eyJzdWIiOiJsZWdhY3lAZnV0c3ByaW5nLmNvbSIsInVzZXJJZCI6NDIsImVtYWlsIjoibGVnYWN5QGZ1dHNwcmluZy5jb20iLCJ2ZXIiOjMs"
            + "ImlhdCI6MTc2MTcwMDAwMCwiZXhwIjo0MTAyNDQ0ODAwfQ.PEYkeK-Xfp1D6ritJnzEasx7Hw30XMy5M3W1DedWYjw";

    @Test
    void legacyToken_isStillAcceptedWithTheSameClaims() {
        Claims claims = jwtService.extractAllClaims(LEGACY_TOKEN);
        assertThat(claims.getSubject()).isEqualTo("legacy@futspring.com");
        assertThat(claims.get("userId", Long.class)).isEqualTo(42L);
        assertThat(claims.get("email", String.class)).isEqualTo("legacy@futspring.com");
        assertThat(claims.get(JwtService.VERSION_CLAIM, Integer.class)).isEqualTo(3);
    }

    @Test
    void generateToken_isStillSignedWithHs256_evenWithALongSecret() {
        JwtConfig longConfig = new JwtConfig();
        ReflectionTestUtils.setField(longConfig, "secret", "x".repeat(64));
        ReflectionTestUtils.setField(longConfig, "expirationMs", 3600000L);

        String token = new JwtService(longConfig).generateToken(1L, "user@example.com", 0);
        String header = new String(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))),
                StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    void generateToken_carriesTheTokenVersion() {
        String token = jwtService.generateToken(1L, "user@example.com", 4);
        assertThat(jwtService.extractAllClaims(token).get(JwtService.VERSION_CLAIM, Integer.class)).isEqualTo(4);
    }
}
