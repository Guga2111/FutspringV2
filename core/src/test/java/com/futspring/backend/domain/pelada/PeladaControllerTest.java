package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.chat.ChatService;
import com.futspring.backend.domain.chat.MessageRepository;
import com.futspring.backend.domain.daily.DailyResultsService;
import com.futspring.backend.domain.daily.dto.PopulateDailyRequestDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.dto.AddPlayerRequestDTO;
import com.futspring.backend.domain.pelada.dto.CreatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.dto.SetAdminRequestDTO;
import com.futspring.backend.domain.pelada.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.domain.stats.repository.RankingRepository;
import com.futspring.backend.domain.stats.repository.StatsRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PeladaControllerTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private DailyRepository dailyRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private RankingRepository rankingRepository;
    @Autowired private StatsRepository statsRepository;
    @Autowired private DailyResultsService dailyResultsService;
    @Autowired private ChatService chatService;

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

    // --- GET /api/v1/peladas/{id}/members/{userId}/history ---

    @Test
    void getPlayerHistory_asMember() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/members/" + admin.getId() + "/history")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(admin.getId()))
                .andExpect(jsonPath("$.rows").isArray());
    }

    @Test
    void getPlayerHistory_withLimit() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/members/" + admin.getId() + "/history")
                .param("limit", "5")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(0))
                .andExpect(jsonPath("$.rows").isArray());
    }

    @Test
    void getPlayerHistory_invalidLimit() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/members/" + admin.getId() + "/history")
                .param("limit", "0")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPlayerHistory_asOutsider() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/members/" + admin.getId() + "/history")
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPlayerHistory_targetNotMember() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/members/" + outsider.getId() + "/history")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isNotFound());
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

    // --- cascade delete and summaries ---

    @Test
    void deletePelada_withFinishedDailyAndMessages_deletesEverythingAndRebuildsStats() throws Exception {
        Daily daily = dailyRepository.save(Daily.builder()
                .pelada(pelada).dailyDate(LocalDate.now().minusDays(1)).dailyTime("18:00").build());
        dailyResultsService.populateFromMessage(daily.getId(), twoTeamsOneMatch(), admin.getEmail());
        dailyResultsService.finalizeDaily(daily.getId(), List.of(), List.of(), admin.getEmail());
        chatService.saveAndBroadcast(pelada.getId(), member.getEmail(), "Bora!");
        assertThat(statsRepository.findByUser(admin).orElseThrow().getGoals()).isEqualTo(2);

        mockMvc.perform(delete("/api/v1/peladas/" + pelada.getId())
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isNoContent());

        assertThat(peladaRepository.findById(pelada.getId())).isEmpty();
        assertThat(dailyRepository.findById(daily.getId())).isEmpty();
        assertThat(messageRepository.count()).isZero();
        assertThat(rankingRepository.count()).isZero();
        // Global stats no longer count the deleted pelada's session
        assertThat(statsRepository.findByUser(admin).orElseThrow().getGoals()).isZero();
        assertThat(statsRepository.findByUser(admin).orElseThrow().getSessionsPlayed()).isZero();
    }

    @Test
    void getMyPeladas_includesNextSessionAndMemberCount() throws Exception {
        dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().plusDays(9)).dailyTime("18:00").build());
        dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().plusDays(2)).dailyTime("18:00").build());
        dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().plusDays(1)).dailyTime("18:00")
                .status(DailyStatus.CANCELED).build());
        dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().minusDays(7)).dailyTime("18:00").build());

        mockMvc.perform(get("/api/v1/peladas/my")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberCount").value(2))
                .andExpect(jsonPath("$[0].nextDailyDate").value(LocalDate.now().plusDays(2).toString()));
    }

    @Test
    void setAdmin_nonMember_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId() + "/players/" + outsider.getId() + "/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(new SetAdminRequestDTO(true))))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPelada_portugueseDayOfWeek_returnsValidationError() throws Exception {
        CreatePeladaRequestDTO req = new CreatePeladaRequestDTO(
                "New Pelada", "Sabado", "10:00", 1.5f, null, null, false, 2, 5);

        mockMvc.perform(post("/api/v1/peladas")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dayOfWeek").exists());
    }

    @Test
    void getMessages_pageSizeIsCapped() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/messages")
                .param("size", "100000").param("page", "-3")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk());
    }

    // admin's team beats member's team 2-0; admin scores both goals
    private PopulateDailyRequestDTO twoTeamsOneMatch() {
        PopulateDailyRequestDTO.ParsedPlayerDTO p1 = new PopulateDailyRequestDTO.ParsedPlayerDTO();
        p1.setUserId(admin.getId());
        p1.setTotalGoals(2);
        PopulateDailyRequestDTO.ParsedPlayerDTO p2 = new PopulateDailyRequestDTO.ParsedPlayerDTO();
        p2.setUserId(member.getId());

        PopulateDailyRequestDTO.ParsedTeamDTO red = new PopulateDailyRequestDTO.ParsedTeamDTO();
        red.setColorName("Vermelho");
        red.setColorHex("#ef4444");
        red.setPlayers(List.of(p1));
        PopulateDailyRequestDTO.ParsedTeamDTO blue = new PopulateDailyRequestDTO.ParsedTeamDTO();
        blue.setColorName("Azul");
        blue.setColorHex("#3b82f6");
        blue.setPlayers(List.of(p2));

        PopulateDailyRequestDTO.ParsedMatchDTO match = new PopulateDailyRequestDTO.ParsedMatchDTO();
        match.setTeam1ColorName("Vermelho");
        match.setTeam1Score(2);
        match.setTeam2ColorName("Azul");
        match.setTeam2Score(0);

        PopulateDailyRequestDTO request = new PopulateDailyRequestDTO();
        request.setTeams(List.of(red, blue));
        request.setMatches(List.of(match));
        return request;
    }
}
