package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.config.JwtConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessTokenVerifierTest {

    static final String SECRET = "test-secret-key-that-is-at-least-32-characters-long";
    static final String EMAIL = "user@example.com";

    @Mock
    UserRepository userRepository;

    JwtService jwtService;
    AccessTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        JwtConfig jwtConfig = new JwtConfig();
        ReflectionTestUtils.setField(jwtConfig, "secret", SECRET);
        ReflectionTestUtils.setField(jwtConfig, "expirationMs", 3600000L);
        jwtService = new JwtService(jwtConfig);
        verifier = new AccessTokenVerifier(jwtService, userRepository);
    }

    @Test
    void verify_currentVersion_returnsTheEmail() {
        when(userRepository.findTokenVersionByEmail(EMAIL)).thenReturn(Optional.of(2));

        assertThat(verifier.verify(jwtService.generateToken(1L, EMAIL, 2))).contains(EMAIL);
    }

    @Test
    void verify_tokenIssuedBeforeAPasswordReset_isRejected() {
        when(userRepository.findTokenVersionByEmail(EMAIL)).thenReturn(Optional.of(3));

        assertThat(verifier.verify(jwtService.generateToken(1L, EMAIL, 2))).isEmpty();
    }

    @Test
    void verify_deletedUser_isRejected() {
        when(userRepository.findTokenVersionByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(verifier.verify(jwtService.generateToken(1L, EMAIL, 0))).isEmpty();
    }

    // Tokens issued before the "ver" claim existed keep working until the first reset
    @Test
    void verify_tokenWithoutVersionClaim_countsAsVersionZero() {
        String legacyToken = Jwts.builder()
                .setSubject(EMAIL)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        when(userRepository.findTokenVersionByEmail(EMAIL)).thenReturn(Optional.of(0));

        assertThat(verifier.verify(legacyToken)).contains(EMAIL);
    }

    @Test
    void verify_badSignature_isRejectedWithoutQueryingTheDatabase() {
        assertThat(verifier.verify("not.a.jwt")).isEmpty();
        verifyNoInteractions(userRepository);
    }
}
