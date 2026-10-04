package com.futspring.backend.domain.user;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.domain.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PeladaRepository peladaRepository;

    private User userA;
    private User userB;
    private User stranger;
    private Pelada shared;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(User.builder()
                .email("ua@test.com").username("userA").password("hash").build());
        userB = userRepository.save(User.builder()
                .email("ub@test.com").username("userB").password("hash").build());
        stranger = userRepository.save(User.builder()
                .email("stranger@test.com").username("stranger").password("hash").build());
        // userA and userB share a pelada; stranger shares none
        shared = peladaRepository.save(Pelada.builder()
                .name("Shared").dayOfWeek("FRIDAY").timeOfDay("18:00").duration(2f).creator(userA)
                .members(new HashSet<>(Set.of(userA, userB))).admins(new HashSet<>(Set.of(userA)))
                .build());
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

    // --- PII and access rules ---

    @Test
    void searchUsers_matchesEmailButNeverReturnsIt() throws Exception {
        mockMvc.perform(get("/api/v1/users/search")
                .param("q", "ub@test")
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("userB"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void searchUsers_queryTooShort_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/users/search")
                .param("q", "u")
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProfile_otherUser_hidesEmail() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userB.getId())
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("userB"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void getProfile_self_includesEmail() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userA.getId())
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(jsonPath("$.email").value("ua@test.com"));
    }

    @Test
    void getStats_teammate_isAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userB.getId() + "/stats")
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void getStats_stranger_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userA.getId() + "/stats")
                .header("Authorization", bearerToken(stranger.getId(), stranger.getEmail())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/" + userA.getId() + "/stats/matches")
                .header("Authorization", bearerToken(stranger.getId(), stranger.getEmail())))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/v1/users/{id}/peladas ---

    @Test
    void getSharedPeladas_returnsOnlyPeladasInCommon() throws Exception {
        peladaRepository.save(Pelada.builder()
                .name("Only B").dayOfWeek("MONDAY").timeOfDay("20:00").duration(1f).creator(userB)
                .members(new HashSet<>(Set.of(userB))).admins(new HashSet<>(Set.of(userB)))
                .build());

        mockMvc.perform(get("/api/v1/users/" + userB.getId() + "/peladas")
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Shared"))
                .andExpect(jsonPath("$[0].memberCount").value(2));
    }

    @Test
    void getSharedPeladas_stranger_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + userA.getId() + "/peladas")
                .header("Authorization", bearerToken(stranger.getId(), stranger.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getSharedPeladas_unknownUser_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999/peladas")
                .header("Authorization", bearerToken(userA.getId(), userA.getEmail())))
                .andExpect(status().isNotFound());
    }
}
