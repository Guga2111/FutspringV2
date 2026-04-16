package com.futspring.backend.controller;

import com.futspring.backend.BaseIntegrationTest;
import com.futspring.backend.dto.AddPlayerRequestDTO;
import com.futspring.backend.dto.CreatePeladaRequestDTO;
import com.futspring.backend.dto.SetAdminRequestDTO;
import com.futspring.backend.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PeladaControllerTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;

    private User admin;
    private User member;
    private User outsider;
    private Pelada pelada;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .email("padmin@test.com").username("padmin").password("hash").build());
        member = userRepository.save(User.builder()
                .email("pmember@test.com").username("pmember").password("hash").build());
        outsider = userRepository.save(User.builder()
                .email("pout@test.com").username("pout").password("hash").build());

        pelada = peladaRepository.save(Pelada.builder()
                .name("Test Pelada")
                .dayOfWeek("FRIDAY")
                .timeOfDay("18:00")
                .duration(2f)
                .creator(admin)
                .members(new HashSet<>(Set.of(admin, member)))
                .admins(new HashSet<>(Set.of(admin)))
                .build());
    }

    // --- POST /api/v1/peladas ---

    @Test
    void createPelada_success() throws Exception {
        CreatePeladaRequestDTO req = new CreatePeladaRequestDTO(
                "New Pelada", "SATURDAY", "10:00", 1.5f, null, null, false, 2, 5);

        mockMvc.perform(post("/api/v1/peladas")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Pelada"));
    }

    @Test
    void createPelada_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/peladas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/v1/peladas/my ---

    @Test
    void getMyPeladas_success() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/my")
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- GET /api/v1/peladas/{id} ---

    @Test
    void getPeladaDetail_asMember() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void getPeladaDetail_asOutsider() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId())
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail())))
                .andExpect(status().is4xxClientError());
    }

    // --- POST /api/v1/peladas/{id}/players ---

    @Test
    void addPlayer_asAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/peladas/" + pelada.getId() + "/players")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(new AddPlayerRequestDTO(outsider.getId()))))
                .andExpect(status().isOk());
    }

    @Test
    void addPlayer_asNonAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/peladas/" + pelada.getId() + "/players")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(member.getId(), member.getEmail()))
                .content(objectMapper.writeValueAsString(new AddPlayerRequestDTO(outsider.getId()))))
                .andExpect(status().isForbidden());
    }

    // --- DELETE /api/v1/peladas/{id}/players/{userId} ---

    @Test
    void removePlayer_asAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/peladas/" + pelada.getId() + "/players/" + member.getId())
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isOk());
    }

    // --- PUT /api/v1/peladas/{id}/players/{userId}/admin ---

    @Test
    void setAdminStatus_asAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId() + "/players/" + member.getId() + "/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(new SetAdminRequestDTO(true))))
                .andExpect(status().isOk());
    }

    // --- PUT /api/v1/peladas/{id} ---

    @Test
    void updatePelada_asAdmin() throws Exception {
        UpdatePeladaRequestDTO req = UpdatePeladaRequestDTO.builder().name("Updated Name").build();

        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void updatePelada_asNonAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(member.getId(), member.getEmail()))
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    // --- DELETE /api/v1/peladas/{id} ---

    @Test
    void deletePelada_asAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/peladas/" + pelada.getId())
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletePelada_asNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/peladas/" + pelada.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/v1/peladas/{id}/ranking ---

    @Test
    void getRanking_asMember() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/ranking")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- GET /api/v1/peladas/{id}/awards ---

    @Test
    void getAwards_asMember() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/awards")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk());
    }

    // --- GET /api/v1/peladas/{id}/messages ---

    @Test
    void getMessages_asMember() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/messages")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- POST /api/v1/peladas/{id}/image ---

    @Test
    void uploadImage_asAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "banner.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

        mockMvc.perform(multipart("/api/v1/peladas/" + pelada.getId() + "/image")
                .file(file)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void uploadImage_asNonAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "banner.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/peladas/" + pelada.getId() + "/image")
                .file(file)
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden());
    }
}
