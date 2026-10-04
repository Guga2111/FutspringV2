package com.futspring.backend.controller;

import com.futspring.backend.BaseIntegrationTest;
import com.futspring.backend.dto.UpdateProfileRequest;
import com.futspring.backend.entity.User;
import com.futspring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(User.builder()
                .email("ua@test.com").username("userA").password("hash").build());
        userB = userRepository.save(User.builder()
                .email("ub@test.com").username("userB").password("hash").build());
    }

    // --- GET /api/v1/users/search ---

    @Test
    void searchUsers_authenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/search")
                .param("q", "user")
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void searchUsers_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/search").param("q", "user"))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/v1/users/{id} ---

    @Test
    void getProfile_authenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userA.getId())
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void getProfile_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userA.getId()))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/v1/users/{id}/stats ---

    @Test
    void getStats_authenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userA.getId() + "/stats")
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void getStats_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userA.getId() + "/stats"))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/v1/users/{id}/stats/timeline ---

    @Test
    void getTimeline_withDateRange() throws Exception {
        LocalDate from = LocalDate.now().minusMonths(1);
        LocalDate to = LocalDate.now();

        mockMvc.perform(get("/api/v1/users/" + userA.getId() + "/stats/timeline")
                .param("from", from.toString())
                .param("to", to.toString())
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk());
    }

    // --- GET /api/v1/users/{id}/stats/matches ---

    @Test
    void getMatchHistory_authenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userA.getId() + "/stats/matches")
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk());
    }

    // --- PUT /api/v1/users/{id} ---

    @Test
    void updateProfile_ownUser() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("updatedName");

        mockMvc.perform(put("/api/v1/users/" + userA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail()))
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("updatedName"));
    }

    @Test
    void updateProfile_otherUser() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("hacker");

        mockMvc.perform(put("/api/v1/users/" + userA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(userB.getId(), userB.getEmail()))
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- POST /api/v1/users/{id}/image ---

    @Test
    void uploadProfileImage_ownUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

        mockMvc.perform(multipart("/api/v1/users/" + userA.getId() + "/image")
                .file(file)
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void uploadProfileImage_otherUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/users/" + userA.getId() + "/image")
                .file(file)
                .header("Authorization", bearerToken(userB.getId(), userB.getEmail())))
                .andExpect(status().isForbidden());
    }

    // --- POST /api/v1/users/{id}/background-image ---

    @Test
    void uploadBackgroundImage_ownUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bg.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

        mockMvc.perform(multipart("/api/v1/users/" + userA.getId() + "/background-image")
                .file(file)
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void uploadBackgroundImage_otherUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bg.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/users/" + userA.getId() + "/background-image")
                .file(file)
                .header("Authorization", bearerToken(userB.getId(), userB.getEmail())))
                .andExpect(status().isForbidden());
    }
}
