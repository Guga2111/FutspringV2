package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Separate class from AuthControllerTest: the rate limit buckets live in the context, and that class
// exhausts the login bucket. The e-mail is sent after the commit, which never happens in these
// rolled-back tests (the listener has its own test)
class PasswordResetControllerTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("resetuser")
                .email("reset@example.com")
                .password(passwordEncoder.encode("oldpassword"))
                .build());
    }

    // --- forgot-password ---

    @Test
    void forgotPassword_knownEmail_returns204AndStoresAToken() throws Exception {
        postJson("/api/v1/auth/forgot-password", Map.of("email", "reset@example.com"))
                .andExpect(status().isNoContent());

        assertThat(tokenRepository.findAll()).singleElement()
                .satisfies(t -> assertThat(t.getUser()).isEqualTo(user));
    }

    @Test
    void forgotPassword_unknownEmail_returnsTheSame204() throws Exception {
        postJson("/api/v1/auth/forgot-password", Map.of("email", "nobody@example.com"))
                .andExpect(status().isNoContent());

        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void forgotPassword_invalidEmail_returns400() throws Exception {
        postJson("/api/v1/auth/forgot-password", Map.of("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    // --- reset-password ---

    @Test
    void resetPassword_validToken_changesPasswordOnlyOnce() throws Exception {
        saveToken("valid-token", LocalDateTime.now().plusMinutes(30));
        Map<String, String> body = Map.of("token", "valid-token", "newPassword", "newpassword");

        postJson("/api/v1/auth/reset-password", body).andExpect(status().isNoContent());
        postJson("/api/v1/auth/login", Map.of("email", "reset@example.com", "password", "newpassword"))
                .andExpect(status().isOk());
        postJson("/api/v1/auth/login", Map.of("email", "reset@example.com", "password", "oldpassword"))
                .andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/reset-password", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordResetService.INVALID_LINK_MESSAGE));
    }

    @Test
    void resetPassword_expiredToken_returns400() throws Exception {
        saveToken("expired-token", LocalDateTime.now().minusMinutes(1));

        postJson("/api/v1/auth/reset-password", Map.of("token", "expired-token", "newPassword", "newpassword"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_unknownToken_returns400() throws Exception {
        postJson("/api/v1/auth/reset-password", Map.of("token", "made-up", "newPassword", "newpassword"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_shortPassword_returns400() throws Exception {
        saveToken("valid-token", LocalDateTime.now().plusMinutes(30));

        postJson("/api/v1/auth/reset-password", Map.of("token", "valid-token", "newPassword", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    private void saveToken(String rawToken, LocalDateTime expiresAt) {
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(ResetTokens.hash(rawToken))
                .expiresAt(expiresAt)
                .build());
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
