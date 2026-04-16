package com.futspring.backend.service;

import com.futspring.backend.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

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
        String token = jwtService.generateToken(1L, "user@example.com");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void generateToken_subjectIsEmail() {
        String token = jwtService.generateToken(1L, "user@example.com");
        assertThat(jwtService.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void generateToken_userIdClaimIsCorrect() {
        String token = jwtService.generateToken(77L, "user@example.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(77L);
    }

    @Test
    void generateToken_emailClaimIsCorrect() {
        String token = jwtService.generateToken(1L, "test@domain.com");
        Claims claims = jwtService.extractAllClaims(token);
        assertThat(claims.get("email", String.class)).isEqualTo("test@domain.com");
    }

    @Test
    void generateToken_isValidImmediately() {
        String token = jwtService.generateToken(1L, "user@example.com");
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    // --- extractAllClaims ---

    @Test
    void extractAllClaims_validToken_returnsClaims() {
        String token = jwtService.generateToken(5L, "a@b.com");
        Claims claims = jwtService.extractAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo("a@b.com");
    }

    @Test
    void extractAllClaims_tamperedToken_throwsJwtException() {
        String token = jwtService.generateToken(1L, "user@example.com");
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

        String token = otherService.generateToken(1L, "user@example.com");
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

        String token = expiredService.generateToken(1L, "user@example.com");
        assertThatThrownBy(() -> jwtService.extractAllClaims(token))
                .isInstanceOf(JwtException.class);
    }

    // --- isTokenValid ---

    @Test
    void isTokenValid_validToken_returnsTrue() {
        String token = jwtService.generateToken(1L, "user@example.com");
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        JwtConfig expiredConfig = new JwtConfig();
        ReflectionTestUtils.setField(expiredConfig, "secret",
                "test-secret-key-that-is-at-least-32-characters-long");
        ReflectionTestUtils.setField(expiredConfig, "expirationMs", -1000L);
        JwtService expiredService = new JwtService(expiredConfig);

        String token = expiredService.generateToken(1L, "user@example.com");
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_malformedToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("not.a.valid.jwt")).isFalse();
    }

    @Test
    void isTokenValid_emptyString_returnsFalse() {
        assertThat(jwtService.isTokenValid("")).isFalse();
    }

    // --- extractEmail / extractUserId ---

    @Test
    void extractEmail_returnsCorrectEmail() {
        String token = jwtService.generateToken(1L, "extract@test.com");
        assertThat(jwtService.extractEmail(token)).isEqualTo("extract@test.com");
    }

    @Test
    void extractUserId_returnsCorrectId() {
        String token = jwtService.generateToken(123L, "user@example.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(123L);
    }
}
