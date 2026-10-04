package com.futspring.backend.controller;

import com.futspring.backend.BaseIntegrationTest;
import com.futspring.backend.dto.LoginRequestDTO;
import com.futspring.backend.dto.RegisterRequestDTO;
import com.futspring.backend.entity.User;
import com.futspring.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void register_success() throws Exception {
        RegisterRequestDTO req = new RegisterRequestDTO();
        req.setUsername("newuser");
        req.setEmail("newuser@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void register_duplicateEmail() throws Exception {
        userRepository.save(User.builder()
                .username("existing")
                .email("dup@example.com")
                .password(passwordEncoder.encode("password123"))
                .build());

        RegisterRequestDTO req = new RegisterRequestDTO();
        req.setUsername("another");
        req.setEmail("dup@example.com");
        req.setPassword("password123");

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

        LoginRequestDTO req = new LoginRequestDTO();
        req.setEmail("login@example.com");
        req.setPassword("password123");

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

        LoginRequestDTO req = new LoginRequestDTO();
        req.setEmail("wrongpw@example.com");
        req.setPassword("badpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownEmail() throws Exception {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setEmail("nobody@example.com");
        req.setPassword("password123");

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

        LoginRequestDTO req = new LoginRequestDTO();
        req.setEmail("rate@example.com");
        req.setPassword("password123");
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
    }
}
