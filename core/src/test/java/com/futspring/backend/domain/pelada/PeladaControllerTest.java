package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.chat.ChatService;
import com.futspring.backend.domain.chat.MessageRepository;
import com.futspring.backend.domain.daily.DailyResultsService;
import com.futspring.backend.domain.daily.dto.PopulateDailyRequestDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.daily.entity.Team;
import com.futspring.backend.domain.pelada.dto.AddPlayerRequestDTO;
import com.futspring.backend.domain.pelada.dto.CreatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.dto.SetAdminRequestDTO;
import com.futspring.backend.domain.pelada.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.domain.stats.repository.RankingRepository;
import com.futspring.backend.domain.stats.repository.StatsRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
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
    @Autowired private DailyAttendanceRepository attendanceRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private DailyAttendanceBumpRepository bumpRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private RankingRepository rankingRepository;
    @Autowired private StatsRepository statsRepository;
    @Autowired private DailyResultsService dailyResultsService;
    @Autowired private ChatService chatService;
    @Autowired private EntityManager entityManager;
    @Autowired private PeladaMensalistaRepository mensalistaRepository;

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
                "New Pelada", "SATURDAY", "10:00", 1.5f, null, null, false, 2, 5, null, 0);

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfTeams").value(pelada.getNumberOfTeams()))
                .andExpect(jsonPath("$.playersPerTeam").value(pelada.getPlayersPerTeam()));
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

    @Test
    void removePlayer_freesTheirSpotInUpcomingSessionsOnly() throws Exception {
        Daily upcoming = dailyRepository.save(Daily.builder()
                .pelada(pelada).dailyDate(LocalDate.now().plusDays(7)).dailyTime("18:00").build());
        Daily live = dailyRepository.save(Daily.builder()
                .pelada(pelada).dailyDate(LocalDate.now()).dailyTime("18:00").status(DailyStatus.IN_COURSE).build());
        for (Daily daily : List.of(upcoming, live)) {
            attendanceRepository.save(new DailyAttendance(daily, admin, Instant.now()));
            attendanceRepository.save(new DailyAttendance(daily, member, Instant.now()));
            teamRepository.save(Team.builder().daily(daily).name("Time 1")
                    .players(new HashSet<>(Set.of(admin, member))).build());
        }

        mockMvc.perform(delete("/api/v1/peladas/" + pelada.getId() + "/players/" + member.getId())
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isOk());

        assertThat(attendanceRepository.findUsersByDaily(upcoming)).containsExactly(admin);
        assertThat(teamRepository.findByDailyWithPlayers(upcoming).get(0).getPlayers()).containsExactly(admin);
        // A session already under way is history: the player stays where they were
        assertThat(attendanceRepository.findUsersByDaily(live)).containsExactlyInAnyOrder(admin, member);
        assertThat(teamRepository.findByDailyWithPlayers(live).get(0).getPlayers()).containsExactlyInAnyOrder(admin, member);
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

    // --- PUT /api/v1/peladas/{id}/players/{userId}/stars ---

    @Test
    void setPlayerStars_asAdmin_isShownInTheDetailAndProfile() throws Exception {
        mockMvc.perform(put(starsUrl(member))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"stars\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(jsonPath("$.stars").value(5));

        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(jsonPath("$.members[?(@.id == " + member.getId() + ")].stars").value(5))
                .andExpect(jsonPath("$.members[?(@.id == " + admin.getId() + ")].stars").value(3));

        mockMvc.perform(get("/api/v1/users/" + member.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(jsonPath("$.stars").value(5));
    }

    @Test
    void setPlayerStars_asNonAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(put(starsUrl(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(member.getId(), member.getEmail()))
                .content("{\"stars\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void setPlayerStars_nonMember_returnsNotFound() throws Exception {
        mockMvc.perform(put(starsUrl(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"stars\":4}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setPlayerStars_outOfRange_returnsValidationError() throws Exception {
        for (String body : List.of("{\"stars\":0}", "{\"stars\":6}", "{}")) {
            mockMvc.perform(put(starsUrl(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.stars").exists());
        }
    }

    private String starsUrl(User target) {
        return "/api/v1/peladas/" + pelada.getId() + "/players/" + target.getId() + "/stars";
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

    @Test
    void updatePelada_capacity_isSaved() throws Exception {
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"numberOfTeams\":3,\"playersPerTeam\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfTeams").value(3))
                .andExpect(jsonPath("$.playersPerTeam").value(6));
    }

    @Test
    void updatePelada_capacityOutOfRange_returnsFieldErrors() throws Exception {
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"numberOfTeams\":1,\"playersPerTeam\":21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.numberOfTeams").value("Mínimo de 2 times"))
                .andExpect(jsonPath("$.errors.playersPerTeam").value("Máximo de 20 jogadores por time"));
    }

    // --- PUT /api/v1/peladas/{id}/creator ---

    @Test
    void transferCreator_toAnAdmin() throws Exception {
        pelada.getAdmins().add(member);
        peladaRepository.save(pelada);

        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId() + "/creator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"userId\":" + member.getId() + "}"))
                .andExpect(status().isOk());

        Pelada saved = peladaRepository.findById(pelada.getId()).orElseThrow();
        assertThat(saved.getCreator().getId()).isEqualTo(member.getId());
    }

    @Test
    void transferCreator_asAdminNotCreator_returnsForbidden() throws Exception {
        pelada.getAdmins().add(member);
        peladaRepository.save(pelada);

        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId() + "/creator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(member.getId(), member.getEmail()))
                .content("{\"userId\":" + member.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void transferCreator_asNonMember_returnsForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId() + "/creator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail()))
                .content("{\"userId\":" + member.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void transferCreator_toANonMember_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId() + "/creator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"userId\":" + outsider.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void transferCreator_toAMemberNotAdmin_returnsConflict() throws Exception {
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId() + "/creator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"userId\":" + member.getId() + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void transferCreator_withoutUserId_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId() + "/creator")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").value("Informe o novo criador"));
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

    // --- GET /api/v1/peladas/{id}/attendance ---

    @Test
    void getAttendanceRanking_asMember() throws Exception {
        mensalistaRepository.save(PeladaMensalista.builder().pelada(pelada).user(member).build());

        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/attendance")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(0))
                .andExpect(jsonPath("$.from").value(LocalDate.now().minusMonths(6).toString()))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[?(@.userId == " + member.getId() + ")].isMensalista").value(true))
                .andExpect(jsonPath("$.rows[?(@.userId == " + admin.getId() + ")].isMensalista").value(false));
    }

    @Test
    void getAttendanceRanking_asOutsider() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/attendance")
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAttendanceRanking_unknownPelada() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/999999/attendance")
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

    // --- DELETE /api/v1/peladas/{id}/image ---

    @Test
    void removeImage_asAdmin() throws Exception {
        pelada.setImage("old.png");
        peladaRepository.save(pelada);

        mockMvc.perform(delete("/api/v1/peladas/" + pelada.getId() + "/image")
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image").doesNotExist());
    }

    @Test
    void removeImage_asNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/peladas/" + pelada.getId() + "/image")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeImage_asNonMember() throws Exception {
        mockMvc.perform(delete("/api/v1/peladas/" + pelada.getId() + "/image")
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail())))
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
        // The delete runs in its own request in production: start from an empty persistence context, or the
        // stats and notifications built above stay managed and Hibernate 6.6 rejects them once their daily is gone
        entityManager.flush();
        entityManager.clear();

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
        Daily next = dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().plusDays(2)).dailyTime("20:30").build());
        attendanceRepository.save(new DailyAttendance(next, member, Instant.now()));
        dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().plusDays(1)).dailyTime("18:00")
                .status(DailyStatus.CANCELED).build());
        dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().minusDays(7)).dailyTime("18:00").build());

        mockMvc.perform(get("/api/v1/peladas/my")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberCount").value(2))
                .andExpect(jsonPath("$[0].isAdmin").value(false))
                .andExpect(jsonPath("$[0].nextDaily.id").value(next.getId()))
                .andExpect(jsonPath("$[0].nextDaily.date").value(LocalDate.now().plusDays(2).toString()))
                .andExpect(jsonPath("$[0].nextDaily.time").value("20:30"))
                .andExpect(jsonPath("$[0].nextDaily.status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].nextDaily.confirmedCount").value(1))
                .andExpect(jsonPath("$[0].nextDaily.capacity").value(pelada.getNumberOfTeams() * pelada.getPlayersPerTeam()))
                .andExpect(jsonPath("$[0].nextDaily.isConfirmed").value(true));

        mockMvc.perform(get("/api/v1/peladas/my")
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(jsonPath("$[0].isAdmin").value(true))
                .andExpect(jsonPath("$[0].nextDaily.isConfirmed").value(false));
    }

    @Test
    void getMyPeladas_withoutUpcomingSession_hasNullNextDaily() throws Exception {
        dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().minusDays(7)).dailyTime("18:00").build());

        mockMvc.perform(get("/api/v1/peladas/my")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nextDaily").value(org.hamcrest.Matchers.nullValue()));
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
                "New Pelada", "Sabado", "10:00", 1.5f, null, null, false, 2, 5, null, 0);

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

    // --- PUT /api/v1/peladas/{id}/players/{userId}/mensalista ---

    @Test
    void setMensalista_asAdmin_isShownInTheDetail() throws Exception {
        pelada.setMaxMensalistas(1);
        peladaRepository.save(pelada);

        mockMvc.perform(put(mensalistaUrl(pelada, member))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"isMensalista\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(jsonPath("$.isMensalista").value(true));

        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(jsonPath("$.timeZone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.maxMensalistas").value(1))
                .andExpect(jsonPath("$.members[?(@.id == " + member.getId() + ")].isMensalista").value(true))
                .andExpect(jsonPath("$.members[?(@.id == " + admin.getId() + ")].isMensalista").value(false));
    }

    @Test
    void setMensalista_limitFull_returnsConflict() throws Exception {
        mockMvc.perform(put(mensalistaUrl(pelada, member))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"isMensalista\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Limite de mensalistas atingido (0/0)"));
    }

    @Test
    void setMensalista_missingFlag_returnsValidationError() throws Exception {
        mockMvc.perform(put(mensalistaUrl(pelada, member))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isMensalista").exists());
    }

    @Test
    void setMensalista_asMemberOrOutsider_returnsForbidden() throws Exception {
        for (User caller : List.of(member, outsider)) {
            mockMvc.perform(put(mensalistaUrl(pelada, member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerToken(caller.getId(), caller.getEmail()))
                    .content("{\"isMensalista\":true}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void setMensalista_userOfAnotherPelada_returnsNotFound() throws Exception {
        Pelada other = peladaRepository.save(Pelada.builder().name("Other").dayOfWeek("MONDAY").timeOfDay("18:00")
                .duration(1f).creator(outsider).members(new HashSet<>(Set.of(outsider))).admins(new HashSet<>(Set.of(outsider)))
                .build());
        pelada.setMaxMensalistas(5);
        peladaRepository.save(pelada);

        mockMvc.perform(put(mensalistaUrl(pelada, outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"isMensalista\":true}"))
                .andExpect(status().isNotFound());
        // and an admin of another pelada can't mark this pelada's members
        mockMvc.perform(put(mensalistaUrl(pelada, member))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail()))
                .content("{\"isMensalista\":true}"))
                .andExpect(status().isForbidden());
        assertThat(other.getId()).isNotNull();
    }

    @Test
    void updatePelada_timeZoneAndLimit_validated() throws Exception {
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"timeZone\":\"America/Toronto\",\"maxMensalistas\":4}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"maxMensalistas\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.maxMensalistas").exists());
        mockMvc.perform(put("/api/v1/peladas/" + pelada.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("{\"timeZone\":\"Nowhere/City\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Fuso horário inválido"));

        Pelada saved = peladaRepository.findById(pelada.getId()).orElseThrow();
        assertThat(saved.getTimeZone()).isEqualTo("America/Toronto");
        assertThat(saved.getMaxMensalistas()).isEqualTo(4);
    }

    @Test
    void getMyPeladas_nextSessionCarriesTheWindowAndTheCallersUnseenBump() throws Exception {
        Daily next = dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.of(2099, 10, 30)).dailyTime("20:00").build());
        bumpRepository.save(DailyAttendanceBump.builder().daily(next).user(member).bumpedBy(admin)
                .bumpedAt(Instant.parse("2099-10-30T12:00:00Z")).build());

        mockMvc.perform(get("/api/v1/peladas/my")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timeZone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$[0].nextDaily.startsAt").value("2099-10-30T23:00:00Z"))
                .andExpect(jsonPath("$[0].nextDaily.confirmOpensAt").value("2099-10-29T23:00:00Z"))
                .andExpect(jsonPath("$[0].nextDaily.bump.bumpedBy.id").value(admin.getId()))
                .andExpect(jsonPath("$[0].nextDaily.bump.bumpedBy.email").doesNotExist())
                .andExpect(jsonPath("$[0].nextDaily.bump.bumpedAt").value("2099-10-30T12:00:00Z"));

        // The admin wasn't bumped
        mockMvc.perform(get("/api/v1/peladas/my")
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(jsonPath("$[0].nextDaily.bump").doesNotExist());
    }

    // --- bulk member actions: /api/v1/peladas/{id}/players/{admin|mensalista|stars|remove} ---

    @Test
    void bulkSetAdmins_asAdmin_promotesEveryoneAndReturnsTheMembers() throws Exception {
        User third = joinedMember("pthird");

        mockMvc.perform(bulk(put(bulkUrl("admin")), admin, "{\"userIds\":[" + member.getId() + "," + third.getId() + "],\"isAdmin\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(member.getId()))
                .andExpect(jsonPath("$[0].isAdmin").value(true))
                .andExpect(jsonPath("$[1].isAdmin").value(true))
                .andExpect(jsonPath("$[0].stars").value(3));

        assertThat(peladaRepository.findAdminIds(pelada.getId()))
                .containsExactlyInAnyOrder(admin.getId(), member.getId(), third.getId());

        // Idempotent: demoting includes one who already isn't an admin
        mockMvc.perform(bulk(put(bulkUrl("admin")), admin, "{\"userIds\":[" + member.getId() + "],\"isAdmin\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isAdmin").value(false));
    }

    @Test
    void bulkSetAdmins_creatorInTheBatch_forbiddenAndNobodyChanges() throws Exception {
        mockMvc.perform(bulk(put(bulkUrl("admin")), admin, "{\"userIds\":[" + member.getId() + "," + admin.getId() + "],\"isAdmin\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(bulk(put(bulkUrl("admin")), admin, "{\"userIds\":[" + member.getId() + "," + outsider.getId() + "],\"isAdmin\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("pout não é membro desta pelada"));

        assertThat(peladaRepository.findAdminIds(pelada.getId())).containsExactly(admin.getId());
    }

    @Test
    void bulkActions_withoutAdmin_forbidden() throws Exception {
        String body = "{\"userIds\":[" + member.getId() + "],\"isAdmin\":true,\"isMensalista\":true,\"stars\":4}";
        for (User caller : List.of(member, outsider)) {
            mockMvc.perform(bulk(put(bulkUrl("admin")), caller, body)).andExpect(status().isForbidden());
            mockMvc.perform(bulk(put(bulkUrl("mensalista")), caller, body)).andExpect(status().isForbidden());
            mockMvc.perform(bulk(put(bulkUrl("stars")), caller, body)).andExpect(status().isForbidden());
            mockMvc.perform(bulk(post(bulkUrl("remove")), caller, body)).andExpect(status().isForbidden());
        }
    }

    @Test
    void bulkActions_invalidUserIds_validationError() throws Exception {
        String hundredAndOne = java.util.stream.LongStream.rangeClosed(1, 101)
                .mapToObj(Long::toString).collect(java.util.stream.Collectors.joining(","));
        for (String ids : List.of("[]", "[" + member.getId() + "," + member.getId() + "]", "[" + hundredAndOne + "]")) {
            mockMvc.perform(bulk(post(bulkUrl("remove")), admin, "{\"userIds\":" + ids + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.userIds").exists());
        }
        mockMvc.perform(bulk(put(bulkUrl("admin")), admin, "{\"userIds\":[" + member.getId() + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isAdmin").exists());
        for (String stars : List.of("0", "6")) {
            mockMvc.perform(bulk(put(bulkUrl("stars")), admin, "{\"userIds\":[" + member.getId() + "],\"stars\":" + stars + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.stars").exists());
        }
    }

    @Test
    void bulkSetMensalistas_overTheLimit_conflictAndNobodyChanges() throws Exception {
        User third = joinedMember("pthird");
        pelada.setMaxMensalistas(1);
        peladaRepository.save(pelada);
        String body = "{\"userIds\":[" + member.getId() + "," + third.getId() + "],\"isMensalista\":true}";

        mockMvc.perform(bulk(put(bulkUrl("mensalista")), admin, body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Limite de mensalistas atingido: cabem 1, foram selecionados 2"));
        assertThat(mensalistaRepository.findUserIdsByPelada(pelada)).isEmpty();

        pelada.setMaxMensalistas(2);
        peladaRepository.save(pelada);
        mockMvc.perform(bulk(put(bulkUrl("mensalista")), admin, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isMensalista").value(true))
                .andExpect(jsonPath("$[1].isMensalista").value(true));
        assertThat(mensalistaRepository.findUserIdsByPelada(pelada)).containsExactlyInAnyOrder(member.getId(), third.getId());
    }

    @Test
    void bulkSetStars_includingTheCreatorAndTheCaller() throws Exception {
        mockMvc.perform(bulk(put(bulkUrl("stars")), admin, "{\"userIds\":[" + admin.getId() + "," + member.getId() + "],\"stars\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stars").value(5))
                .andExpect(jsonPath("$[1].stars").value(5));

        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(jsonPath("$.members[?(@.id == " + admin.getId() + ")].stars").value(5))
                .andExpect(jsonPath("$.members[?(@.id == " + member.getId() + ")].stars").value(5));
    }

    @Test
    void bulkRemove_freesTheirSpotsInUpcomingSessionsOnly() throws Exception {
        User third = joinedMember("pthird");
        Daily upcoming = dailyRepository.save(Daily.builder()
                .pelada(pelada).dailyDate(LocalDate.now().plusDays(7)).dailyTime("18:00").build());
        Daily live = dailyRepository.save(Daily.builder()
                .pelada(pelada).dailyDate(LocalDate.now()).dailyTime("18:00").status(DailyStatus.IN_COURSE).build());
        for (Daily daily : List.of(upcoming, live)) {
            for (User user : List.of(admin, member, third)) {
                attendanceRepository.save(new DailyAttendance(daily, user, Instant.now()));
            }
            teamRepository.save(Team.builder().daily(daily).name("Time 1")
                    .players(new HashSet<>(Set.of(admin, member, third))).build());
        }
        mensalistaRepository.save(PeladaMensalista.builder().pelada(pelada).user(member).build());

        mockMvc.perform(bulk(post(bulkUrl("remove")), admin, "{\"userIds\":[" + member.getId() + "," + third.getId() + "]}"))
                .andExpect(status().isNoContent());

        assertThat(peladaRepository.findMemberIds(pelada.getId(), List.of(admin.getId(), member.getId(), third.getId())))
                .containsExactly(admin.getId());
        assertThat(mensalistaRepository.findUserIdsByPelada(pelada)).isEmpty();
        assertThat(attendanceRepository.findUsersByDaily(upcoming)).containsExactly(admin);
        assertThat(teamRepository.findByDailyWithPlayers(upcoming).get(0).getPlayers()).containsExactly(admin);
        assertThat(attendanceRepository.findUsersByDaily(live)).containsExactlyInAnyOrder(admin, member, third);
        assertThat(teamRepository.findByDailyWithPlayers(live).get(0).getPlayers()).containsExactlyInAnyOrder(admin, member, third);
    }

    @Test
    void bulkRemove_creatorOrNonMemberInTheBatch_nobodyIsRemoved() throws Exception {
        mockMvc.perform(bulk(post(bulkUrl("remove")), admin, "{\"userIds\":[" + member.getId() + "," + admin.getId() + "]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(bulk(post(bulkUrl("remove")), admin, "{\"userIds\":[" + member.getId() + "," + outsider.getId() + "]}"))
                .andExpect(status().isNotFound());

        assertThat(peladaRepository.findMemberIds(pelada.getId(), List.of(admin.getId(), member.getId())))
                .containsExactlyInAnyOrder(admin.getId(), member.getId());
    }

    @Test
    void bulkActions_targetFromAnotherPelada_notFound() throws Exception {
        Pelada other = peladaRepository.save(Pelada.builder().name("Other").dayOfWeek("MONDAY").timeOfDay("20:00")
                .duration(1f).creator(outsider).members(new HashSet<>(Set.of(outsider))).admins(new HashSet<>(Set.of(outsider))).build());

        mockMvc.perform(bulk(put(bulkUrl("stars")), admin, "{\"userIds\":[" + outsider.getId() + "],\"stars\":1}"))
                .andExpect(status().isNotFound());
        // An admin of another pelada can't act on this one
        mockMvc.perform(bulk(put("/api/v1/peladas/" + other.getId() + "/players/stars"), admin,
                        "{\"userIds\":[" + outsider.getId() + "],\"stars\":1}"))
                .andExpect(status().isForbidden());
    }

    private User joinedMember(String username) {
        User user = userRepository.save(User.builder()
                .email(username + "@test.com").username(username).password("hash").build());
        pelada.getMembers().add(user);
        peladaRepository.save(pelada);
        return user;
    }

    private String bulkUrl(String action) {
        return "/api/v1/peladas/" + pelada.getId() + "/players/" + action;
    }

    private org.springframework.test.web.servlet.RequestBuilder bulk(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, User caller, String body) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(caller.getId(), caller.getEmail()))
                .content(body);
    }

    private static String mensalistaUrl(Pelada pelada, User user) {
        return "/api/v1/peladas/" + pelada.getId() + "/players/" + user.getId() + "/mensalista";
    }
}
