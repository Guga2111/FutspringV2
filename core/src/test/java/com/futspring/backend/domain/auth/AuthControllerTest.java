package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.auth.dto.LoginRequestDTO;
import com.futspring.backend.domain.auth.dto.RegisterRequestDTO;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void register_success() throws Exception {
        RegisterRequestDTO req = new RegisterRequestDTO("newuser", "newuser@example.com", "password123", LegalTerms.CURRENT_VERSION);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists());

        User saved = userRepository.findByEmail("newuser@example.com").orElseThrow();
        assertThat(saved.getTermsVersion()).isEqualTo(LegalTerms.CURRENT_VERSION);
        assertThat(saved.getTermsAcceptedAt()).isNotNull();
    }

    @Test
    void register_withoutTermsVersion_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "newuser", "email": "newuser@example.com", "password": "password123"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.termsVersion").value("Aceite os termos de uso para criar a conta"));
        assertThat(userRepository.findByEmail("newuser@example.com")).isEmpty();
    }

    @Test
    void register_outdatedTermsVersion_isConflict() throws Exception {
        RegisterRequestDTO req = new RegisterRequestDTO("newuser", "newuser@example.com", "password123", "2020-01-01");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Os termos foram atualizados. Recarregue a página para ler a nova versão."));
    }

    @Test
    void register_duplicateEmail() throws Exception {
        userRepository.save(User.builder()
                .username("existing")
                .email("dup@example.com")
                .password(passwordEncoder.encode("password123"))
                .build());

        RegisterRequestDTO req = new RegisterRequestDTO("another", "dup@example.com", "password123", LegalTerms.CURRENT_VERSION);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_success() throws Exception {
        userRepository.save(User.builder()
                .username("loginuser")
                .email("login@example.com")
                .password(passwordEncoder.encode("password123"))
                .build());

        LoginRequestDTO req = new LoginRequestDTO("login@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void login_wrongPassword() throws Exception {
        userRepository.save(User.builder()
                .username("wrongpw")
                .email("wrongpw@example.com")
                .password(passwordEncoder.encode("password123"))
                .build());

        LoginRequestDTO req = new LoginRequestDTO("wrongpw@example.com", "badpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownEmail() throws Exception {
        LoginRequestDTO req = new LoginRequestDTO("nobody@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_rateLimited() throws Exception {
        userRepository.save(User.builder()
                .username("rateuser")
                .email("rate@example.com")
                .password(passwordEncoder.encode("password123"))
                .build());

        LoginRequestDTO req = new LoginRequestDTO("rate@example.com", "password123");
        String body = objectMapper.writeValueAsString(req);

        // Exhaust up to 15 tokens — the bucket holds 10 per minute, so by
        // attempt 11 (even if 0 prior tests consumed tokens) we are rate-limited.
        for (int i = 0; i < 14; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isTooManyRequests());
        // 15 requests against a bucket of 10: at least 5 rejections counted for the route
        assertThat(meterRegistry.get("futspring.ratelimit.rejected").tag("route", "login")
                .counter().count()).isGreaterThanOrEqualTo(5);
    }
}
