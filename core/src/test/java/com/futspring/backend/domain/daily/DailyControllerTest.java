package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.CreateDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.FinalizeDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.SwapPlayersRequestDTO;
import com.futspring.backend.domain.daily.dto.UpdateDailyStatusRequestDTO;
import com.futspring.backend.domain.daily.dto.UpdateTeamColorRequestDTO;
import com.futspring.backend.domain.daily.dto.UpdateTeamNameRequestDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.entity.Team;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaMensalista;
import com.futspring.backend.domain.pelada.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DailyControllerTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private DailyRepository dailyRepository;
    @Autowired private DailyAttendanceRepository attendanceRepository;
    @Autowired private DailyAttendanceBumpRepository bumpRepository;
    @Autowired private PeladaMensalistaRepository mensalistaRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TeamRepository teamRepository;

    private User admin;
    private User member;
    private User outsider;
    private Pelada pelada;
    private Daily daily;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .email("dadmin@test.com").username("dadmin").password("hash").build());
        member = userRepository.save(User.builder()
                .email("dmember@test.com").username("dmember").password("hash").build());
        outsider = userRepository.save(User.builder()
                .email("dout@test.com").username("dout").password("hash").build());

        pelada = peladaRepository.save(Pelada.builder()
                .name("Daily Pelada")
                .dayOfWeek("FRIDAY")
                .timeOfDay("18:00")
                .duration(2f)
                .creator(admin)
                .members(new HashSet<>(Set.of(admin, member)))
                .admins(new HashSet<>(Set.of(admin)))
                .numberOfTeams(2)
                .playersPerTeam(5)
                .build());

        daily = dailyRepository.save(Daily.builder()
                .pelada(pelada)
                .dailyDate(LocalDate.now().plusDays(7))
                .dailyTime("18:00")
                .build());
    }

    // --- POST /api/v1/peladas/{id}/dailies ---

    @Test
    void createDaily_asAdmin() throws Exception {
        CreateDailyRequestDTO req = new CreateDailyRequestDTO(
                LocalDate.now().plusDays(14), "20:00");

        mockMvc.perform(post("/api/v1/peladas/" + pelada.getId() + "/dailies")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void createDaily_asNonAdmin() throws Exception {
        CreateDailyRequestDTO req = new CreateDailyRequestDTO(
                LocalDate.now().plusDays(21), "20:00");

        mockMvc.perform(post("/api/v1/peladas/" + pelada.getId() + "/dailies")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(member.getId(), member.getEmail()))
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/v1/peladas/{id}/dailies ---

    @Test
    void getDailies_asMember() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/dailies")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getDailies_includesCountsAndCallerAttendance() throws Exception {
        attendanceRepository.save(new DailyAttendance(daily, member, Instant.now()));
        teamRepository.save(Team.builder().daily(daily).name("Time 1").build());
        teamRepository.save(Team.builder().daily(daily).name("Time 2").build());

        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/dailies")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].confirmedPlayerCount").value(1))
                .andExpect(jsonPath("$[0].teamCount").value(2))
                .andExpect(jsonPath("$[0].matchCount").value(0))
                .andExpect(jsonPath("$[0].isConfirmed").value(true));

        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/dailies")
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(jsonPath("$[0].isConfirmed").value(false));
    }

    @Test
    void getDailies_asOutsider() throws Exception {
        mockMvc.perform(get("/api/v1/peladas/" + pelada.getId() + "/dailies")
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail())))
                .andExpect(status().is4xxClientError());
    }

    // --- GET /api/v1/dailies/{id} ---

    @Test
    void getDailyDetail_asMember_includesPeladaMembers() throws Exception {
        mockMvc.perform(get("/api/v1/dailies/" + daily.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAdmin").value(false))
                .andExpect(jsonPath("$.peladaMembers.length()").value(2))
                .andExpect(jsonPath("$.peladaMembers[0].email").doesNotExist());
    }

    // --- POST /api/v1/dailies/{id}/confirm ---

    @Test
    void confirmAttendance_self() throws Exception {
        // Today in the pelada's zone: the diarista window (24 h before the start) is open
        daily.setDailyDate(LocalDate.now(ZoneId.of(pelada.getTimeZone())));
        dailyRepository.save(daily);

        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedPlayerCount").value(1))
                .andExpect(jsonPath("$.isConfirmed").value(true));
    }

    @Test
    void confirmAttendance_diaristaBeforeTheWindow_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Diaristas podem confirmar a partir de ")));
    }

    @Test
    void disconfirmAttendance_self() throws Exception {
        // Confirm first so we have something to disconfirm
        attendanceRepository.save(new DailyAttendance(daily, member, Instant.now()));

        mockMvc.perform(delete("/api/v1/dailies/" + daily.getId() + "/confirm")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk());
    }

    // --- POST /api/v1/dailies/{id}/confirm/{userId} ---

    @Test
    void confirmAttendance_adminForOtherUser() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm/" + member.getId())
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void confirmAttendance_nonAdminForOtherUser() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm/" + admin.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden());
    }

    // --- POST /api/v1/dailies/{id}/confirm/all ---

    @Test
    void confirmAll_asAdmin_confirmsEveryMember() throws Exception {
        attendanceRepository.save(new DailyAttendance(daily, member, Instant.now()));

        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm/all")
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedPlayerCount").value(2))
                .andExpect(jsonPath("$.isConfirmed").value(true));
    }

    @Test
    void confirmAll_asNonAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm/all")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmAll_asOutsider_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm/all")
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmAll_unknownDaily_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/999999/confirm/all")
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmAll_lockedDaily_returnsBadRequest() throws Exception {
        daily.setStatus(DailyStatus.IN_COURSE);
        dailyRepository.save(daily);

        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm/all")
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isBadRequest());
    }

    // --- PUT /api/v1/dailies/{id}/status ---

    @Test
    void updateStatus_asAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/dailies/" + daily.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(new UpdateDailyStatusRequestDTO(DailyStatus.CONFIRMED))))
                .andExpect(status().isOk());
    }

    // --- POST /api/v1/dailies/{id}/sort-teams ---

    @Test
    void sortTeams_asAdmin() throws Exception {
        // Create exactly numberOfTeams × playersPerTeam = 10 confirmed players
        for (int i = 0; i < 10; i++) {
            User player = userRepository.save(User.builder()
                    .email("sp" + i + "@test.com").username("sp" + i).password("hash").build());
            attendanceRepository.save(new DailyAttendance(daily, player, Instant.now()));
        }

        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/sort-teams")
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void sortTeams_asNonAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/sort-teams")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden());
    }

    // --- PUT /api/v1/dailies/{id}/teams/swap ---

    @Test
    void swapPlayers_asAdmin() throws Exception {
        User p1 = userRepository.save(User.builder()
                .email("swap1@test.com").username("swap1").password("hash").build());
        User p2 = userRepository.save(User.builder()
                .email("swap2@test.com").username("swap2").password("hash").build());

        teamRepository.save(Team.builder().daily(daily).name("T1")
                .players(new HashSet<>(Set.of(p1))).build());
        teamRepository.save(Team.builder().daily(daily).name("T2")
                .players(new HashSet<>(Set.of(p2))).build());

        mockMvc.perform(put("/api/v1/dailies/" + daily.getId() + "/teams/swap")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(new SwapPlayersRequestDTO(p1.getId(), p2.getId()))))
                .andExpect(status().isOk());
    }

    @Test
    void swapPlayers_asNonAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/dailies/" + daily.getId() + "/teams/swap")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(member.getId(), member.getEmail()))
                .content(objectMapper.writeValueAsString(new SwapPlayersRequestDTO(1L, 2L))))
                .andExpect(status().isForbidden());
    }

    // --- PATCH /api/v1/dailies/{dailyId}/teams/{teamId}/name ---

    @Test
    void updateTeamName_asTeamMember() throws Exception {
        // The service requires caller to be a player on the team
        Team team = teamRepository.save(Team.builder()
                .daily(daily)
                .name("Original")
                .players(new HashSet<>(Set.of(admin)))
                .build());

        mockMvc.perform(patch("/api/v1/dailies/" + daily.getId() + "/teams/" + team.getId() + "/name")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(new UpdateTeamNameRequestDTO("Renamed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void updateTeamName_asOutsider_returnsForbidden() throws Exception {
        Team team = teamRepository.save(Team.builder()
                .daily(daily).name("Original").build());

        mockMvc.perform(patch("/api/v1/dailies/" + daily.getId() + "/teams/" + team.getId() + "/name")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail()))
                .content(objectMapper.writeValueAsString(new UpdateTeamNameRequestDTO("Renamed"))))
                .andExpect(status().isForbidden());
    }

    // --- PATCH /api/v1/dailies/{dailyId}/teams/{teamId}/color ---

    @Test
    void updateTeamColor_asAdmin() throws Exception {
        Team team = teamRepository.save(Team.builder()
                .daily(daily).name("Team 1").build());

        mockMvc.perform(patch("/api/v1/dailies/" + daily.getId() + "/teams/" + team.getId() + "/color")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(new UpdateTeamColorRequestDTO("#3b82f6"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#3b82f6"));
    }

    @Test
    void updateTeamColor_asOutsider_returnsForbidden() throws Exception {
        Team team = teamRepository.save(Team.builder()
                .daily(daily).name("Team 1").build());

        mockMvc.perform(patch("/api/v1/dailies/" + daily.getId() + "/teams/" + team.getId() + "/color")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail()))
                .content(objectMapper.writeValueAsString(new UpdateTeamColorRequestDTO("#ff0000"))))
                .andExpect(status().isForbidden());
    }

    // --- POST /api/v1/dailies/{id}/results ---

    @Test
    void submitResults_asNonAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(member.getId(), member.getEmail()))
                .content("[{\"team1Id\":1,\"team2Id\":2,\"team1Score\":1,\"team2Score\":0}]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitResults_negativeScore_returnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("[{\"team1Id\":1,\"team2Id\":2,\"team1Score\":-1,\"team2Score\":0}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['results[0].team1Score']").exists());
    }

    @Test
    void submitResults_malformedJson_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("[{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void submitResults_adminReachesService() throws Exception {
        // Admin reaches the service; with no teams the service returns 400 (not 401/403)
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/results")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content("[]"))
                .andExpect(status().is(org.hamcrest.Matchers.not(401)))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    // --- POST /api/v1/dailies/{id}/finalize ---

    @Test
    void finalizeDaily_asNonAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/finalize")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(member.getId(), member.getEmail()))
                .content(objectMapper.writeValueAsString(new FinalizeDailyRequestDTO(
                        Collections.emptyList(), Collections.emptyList()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void finalizeDaily_adminReachesService() throws Exception {
        // Admin reaches the service; returns 400 if not in FINISHED state (not 401/403)
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/finalize")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                .content(objectMapper.writeValueAsString(new FinalizeDailyRequestDTO(
                        Collections.emptyList(), Collections.emptyList()))))
                .andExpect(status().is(org.hamcrest.Matchers.not(401)))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    // --- DELETE /api/v1/dailies/{id} ---

    @Test
    void deleteDaily_asAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/dailies/" + daily.getId())
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteDaily_asNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/dailies/" + daily.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden());
    }

    // --- PUT /api/v1/dailies/{id}/champion-image ---

    @Test
    void uploadChampionImage_asAdmin() throws Exception {
        // Champion image upload requires the daily to be in FINISHED status
        daily.setStatus(DailyStatus.FINISHED);
        dailyRepository.save(daily);

        MockMultipartFile file = new MockMultipartFile(
                "file", "champ.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

        mockMvc.perform(multipart("/api/v1/dailies/" + daily.getId() + "/champion-image")
                .file(file)
                .with(request -> { request.setMethod("PUT"); return request; })
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void uploadChampionImage_asNonAdmin_returnsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "champ.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/dailies/" + daily.getId() + "/champion-image")
                .file(file)
                .with(request -> { request.setMethod("PUT"); return request; })
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden());
    }

    // --- window, bumps and the attendance fields ---

    @Test
    void confirmAttendance_ignoresAnyTimeOrUserSentInTheBody() throws Exception {
        String forged = "{\"confirmedAt\":\"2000-01-01T00:00:00Z\",\"now\":\"2099-01-01T00:00:00Z\",\"userId\":" + admin.getId() + "}";
        // The window is closed (session in a week): a forged "now" doesn't open it
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forged)
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden());

        daily.setDailyDate(LocalDate.now(ZoneId.of(pelada.getTimeZone())));
        dailyRepository.save(daily);
        Instant before = Instant.now();
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forged)
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk());

        assertThat(attendanceRepository.findByDailyWithUser(daily)).singleElement().satisfies(a -> {
            assertThat(a.getUser()).isEqualTo(member);
            assertThat(a.getConfirmedAt()).isAfterOrEqualTo(before.minusSeconds(1));
        });
    }

    @Test
    void getDailyDetail_includesCapacityWindowTypesAndBumps() throws Exception {
        mensalistaRepository.save(PeladaMensalista.builder().pelada(pelada).user(admin).build());
        attendanceRepository.save(new DailyAttendance(daily, admin, Instant.parse("2026-10-01T12:00:00Z")));
        bumpRepository.save(DailyAttendanceBump.builder().daily(daily).user(member).bumpedBy(admin)
                .bumpedAt(Instant.parse("2026-10-01T12:00:00Z")).build());

        mockMvc.perform(get("/api/v1/dailies/" + daily.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.timeZone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.startsAt").exists())
                .andExpect(jsonPath("$.confirmOpensAt").exists())
                .andExpect(jsonPath("$.confirmedPlayers[0].isMensalista").value(true))
                .andExpect(jsonPath("$.confirmedPlayers[0].confirmedAt").value("2026-10-01T12:00:00Z"))
                .andExpect(jsonPath("$.peladaMembers[?(@.id == " + member.getId() + ")].isMensalista").value(false))
                .andExpect(jsonPath("$.myBump.bumpedBy.id").value(admin.getId()))
                .andExpect(jsonPath("$.bumps[0].user.id").value(member.getId()))
                .andExpect(jsonPath("$.bumps[0].user.email").doesNotExist());

        // The mensalista has no window and no bump of their own; everyone sees the history
        mockMvc.perform(get("/api/v1/dailies/" + daily.getId())
                .header("Authorization", bearerToken(admin.getId(), admin.getEmail())))
                .andExpect(jsonPath("$.confirmOpensAt").doesNotExist())
                .andExpect(jsonPath("$.myBump").doesNotExist())
                .andExpect(jsonPath("$.bumps.length()").value(1));
    }

    @Test
    void markBumpsSeen_onlyTouchesTheCallersBumps() throws Exception {
        User other = userRepository.save(User.builder().email("dother@test.com").username("dother").password("hash").build());
        pelada.getMembers().add(other);
        peladaRepository.save(pelada);
        DailyAttendanceBump mine = bumpRepository.save(DailyAttendanceBump.builder().daily(daily).user(member).bumpedBy(admin)
                .bumpedAt(Instant.parse("2026-10-01T12:00:00Z")).build());
        DailyAttendanceBump theirs = bumpRepository.save(DailyAttendanceBump.builder().daily(daily).user(other).bumpedBy(admin)
                .bumpedAt(Instant.parse("2026-10-01T12:00:00Z")).build());

        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/bumps/seen")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        assertThat(bumpRepository.findById(mine.getId()).orElseThrow().getSeenAt()).isNotNull();
        assertThat(bumpRepository.findById(theirs.getId()).orElseThrow().getSeenAt()).isNull();
        mockMvc.perform(get("/api/v1/dailies/" + daily.getId())
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(jsonPath("$.myBump").doesNotExist());
    }

    @Test
    void markBumpsSeen_outsider_returnsForbidden_unknownDaily_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/bumps/seen")
                .header("Authorization", bearerToken(outsider.getId(), outsider.getEmail())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/dailies/999999/bumps/seen")
                .header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isNotFound());
    }
}
