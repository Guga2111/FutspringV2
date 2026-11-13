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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;

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
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"));

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
        userRepository.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH)
                .username("existing")
                .email("dup@example.com")
                .password(passwordEncoder.encode("password123"))
                .build());

        RegisterRequestDTO req = new RegisterRequestDTO("another", "dup@example.com", "password123", LegalTerms.CURRENT_VERSION);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    void usernameConflictDoesNotDiscloseWhetherTheEmailExists() throws Exception {
        saveUser("taken-name", "known@example.com");
        for (String email : java.util.List.of("known@example.com", "unknown@example.com")) {
            mockMvc.perform(auth("/api/v1/auth/register").content(objectMapper.writeValueAsString(
                            new RegisterRequestDTO("taken-name", email, "password123", LegalTerms.CURRENT_VERSION))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Este nome de usuário já está em uso"));
        }
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
        userRepository.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH)
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
        userRepository.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH)
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
        userRepository.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH)
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

    // --- input hardening (PR A3): BCrypt's 72 bytes, e-mail case and spaces ---

    private static final AtomicInteger IP = new AtomicInteger();
    // 73 ASCII characters, and 40 "é": 40 characters but 80 bytes, which @Size alone would let through
    private static final String PASSWORD_73 = "a".repeat(73);
    private static final String PASSWORD_80_BYTES = "é".repeat(40);

    @Test
    void register_passwordOver72Characters_is400() throws Exception {
        mockMvc.perform(auth("/api/v1/auth/register").content(objectMapper.writeValueAsString(
                        new RegisterRequestDTO("longo", "longo@example.com", PASSWORD_73, LegalTerms.CURRENT_VERSION))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("A senha é longa demais (até 72 caracteres; letras com acento contam em dobro)"));
    }

    @Test
    void register_passwordOver72Bytes_is400NotA500() throws Exception {
        mockMvc.perform(auth("/api/v1/auth/register").content(objectMapper.writeValueAsString(
                        new RegisterRequestDTO("acentos", "acentos@example.com", PASSWORD_80_BYTES, LegalTerms.CURRENT_VERSION))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("A senha é longa demais (até 72 caracteres; letras com acento contam em dobro)"));
        assertThat(userRepository.findByEmail("acentos@example.com")).isEmpty();
    }

    @Test
    void register_storesTheEmailTrimmedAndInLowerCase() throws Exception {
        mockMvc.perform(auth("/api/v1/auth/register").content("""
                        {"username": "Ana", "email": "  Ana@X.com ", "password": "password123", "termsVersion": "%s"}
                        """.formatted(LegalTerms.CURRENT_VERSION)))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findByEmail("ana@x.com")).isPresent();
    }

    @Test
    void register_emailDifferingOnlyByCase_isConflict() throws Exception {
        saveUser("ana", "ana@x.com");

        mockMvc.perform(auth("/api/v1/auth/register").content(objectMapper.writeValueAsString(
                        new RegisterRequestDTO("outra", "ANA@x.com", "password123", LegalTerms.CURRENT_VERSION))))
                .andExpect(status().isNoContent());
    }

    @Test
    void login_emailWithOtherCaseAndSpaces_findsTheAccount() throws Exception {
        saveUser("ana", "ana@x.com");

        mockMvc.perform(auth("/api/v1/auth/login").content("""
                        {"email": "Ana@X.com ", "password": "password123"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("ana@x.com"));
    }

    @Test
    void login_passwordOver72Bytes_is400NotA500() throws Exception {
        saveUser("ana", "ana@x.com");

        mockMvc.perform(auth("/api/v1/auth/login").content(objectMapper.writeValueAsString(
                        new LoginRequestDTO("ana@x.com", PASSWORD_73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("A senha é longa demais (até 72 caracteres; letras com acento contam em dobro)"));
    }

    private void saveUser(String username, String email) {
        userRepository.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH)
                .username(username)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .build());
    }

    // each request from its own IP, so the 10/min login bucket shared by this class never answers 429 here
    private MockHttpServletRequestBuilder auth(String path) {
        int n = IP.incrementAndGet();
        String ip = "10.3." + (n / 250) + "." + (n % 250 + 1);
        return post(path).with(r -> { r.setRemoteAddr(ip); return r; }).contentType(MediaType.APPLICATION_JSON);
    }
}
