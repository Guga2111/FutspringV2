package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.dto.UpdateDailyStatusRequestDTO;
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
import com.futspring.backend.domain.pelada.dto.InvitePlayerRequestDTO;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private DailyRepository dailyRepository;
    @Autowired private DailyAttendanceRepository attendanceRepository;
    @Autowired private DailyAttendanceBumpRepository bumpRepository;
    @Autowired private PeladaMensalistaRepository mensalistaRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private NotificationPreferenceService preferenceService;
    @Autowired private UnsubscribeTokens unsubscribeTokens;
    @Autowired private EntityManager entityManager;

    private User admin;
    private User member;
    private User other;
    private Pelada pelada;
    private Daily daily;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder().email("nadmin@test.com").username("nadmin").password("hash").build());
        member = userRepository.save(User.builder().email("nmember@test.com").username("nmember").password("hash").build());
        other = userRepository.save(User.builder().email("nother@test.com").username("nother").password("hash").build());
        pelada = peladaRepository.save(Pelada.builder()
                .name("Pelada do Fut")
                .dayOfWeek("SATURDAY")
                .timeOfDay("20:00")
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
                .dailyTime("20:00")
                .build());
    }

    private Notification notify(User recipient, NotificationType type, Instant createdAt, Instant readAt) {
        return notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(type)
                .pelada(pelada)
                .daily(daily)
                .actor(admin)
                .createdAt(createdAt)
                .readAt(readAt)
                .build());
    }

    private String auth(User user) {
        return bearerToken(user.getId(), user.getEmail());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    // --- GET /api/v1/notifications ---

    @Test
    void list_newestFirst_withReferencesAndOnlyTheCallersRows() throws Exception {
        Notification older = notify(member, NotificationType.SESSION_CREATED, Instant.parse("2026-10-01T10:00:00Z"), null);
        Notification newer = notify(member, NotificationType.SESSION_CANCELED, Instant.parse("2026-10-02T10:00:00Z"), null);
        notify(other, NotificationType.SESSION_CREATED, Instant.parse("2026-10-03T10:00:00Z"), null);
        flushAndClear();

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(newer.getId()))
                .andExpect(jsonPath("$[0].type").value("SESSION_CANCELED"))
                .andExpect(jsonPath("$[0].category").value("ATTENDANCE"))
                .andExpect(jsonPath("$[0].pelada.name").value("Pelada do Fut"))
                .andExpect(jsonPath("$[0].daily.id").value(daily.getId()))
                .andExpect(jsonPath("$[0].daily.time").value("20:00"))
                .andExpect(jsonPath("$[0].actor.username").value("nadmin"))
                .andExpect(jsonPath("$[0].actor.email").doesNotExist())
                .andExpect(jsonPath("$[0].readAt").value(nullValue()))
                .andExpect(jsonPath("$[1].id").value(older.getId()));
    }

    @Test
    void list_pagesAndClampsTheSize() throws Exception {
        for (int i = 0; i < 3; i++) {
            notify(member, NotificationType.SESSION_CREATED, Instant.parse("2026-10-01T10:00:00Z").plusSeconds(i), null);
        }
        flushAndClear();

        mockMvc.perform(get("/api/v1/notifications?page=1&size=2").header("Authorization", auth(member)))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/v1/notifications?size=0").header("Authorization", auth(member)))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/v1/notifications?size=500").header("Authorization", auth(member)))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void list_unreadOnly() throws Exception {
        notify(member, NotificationType.SESSION_CREATED, Instant.parse("2026-10-01T10:00:00Z"), Instant.now());
        Notification unread = notify(member, NotificationType.SESSION_CANCELED, Instant.parse("2026-10-02T10:00:00Z"), null);
        flushAndClear();

        mockMvc.perform(get("/api/v1/notifications?unread=true").header("Authorization", auth(member)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(unread.getId()));
    }

    @Test
    void list_teamsSortedCarriesTheCallersTeam() throws Exception {
        Team blue = new Team();
        blue.setDaily(daily);
        blue.setName("Time Azul");
        blue.setColor("blue");
        blue.setPlayers(new HashSet<>(Set.of(member)));
        teamRepository.save(blue);
        notify(member, NotificationType.TEAMS_SORTED, Instant.now(), null);
        notify(member, NotificationType.SESSION_CREATED, Instant.now().minusSeconds(60), null);
        flushAndClear();

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", auth(member)))
                .andExpect(jsonPath("$[0].team.name").value("Time Azul"))
                .andExpect(jsonPath("$[0].team.color").value("blue"))
                .andExpect(jsonPath("$[1].team").value(nullValue()));
    }

    @Test
    void list_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    // --- GET /api/v1/notifications/unread-count ---

    @Test
    void unreadCount_countsOnlyTheCallersUnread() throws Exception {
        notify(member, NotificationType.SESSION_CREATED, Instant.now(), null);
        notify(member, NotificationType.SESSION_CANCELED, Instant.now(), Instant.now());
        notify(other, NotificationType.SESSION_CREATED, Instant.now(), null);
        flushAndClear();

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    // --- POST /api/v1/notifications/{id}/read ---

    @Test
    void markRead_marksAndIsIdempotent() throws Exception {
        Notification n = notify(member, NotificationType.SESSION_CREATED, Instant.now(), null);
        flushAndClear();

        mockMvc.perform(post("/api/v1/notifications/" + n.getId() + "/read").header("Authorization", auth(member)))
                .andExpect(status().isNoContent());
        flushAndClear();
        Instant readAt = notificationRepository.findById(n.getId()).orElseThrow().getReadAt();
        assertThat(readAt).isNotNull();

        mockMvc.perform(post("/api/v1/notifications/" + n.getId() + "/read").header("Authorization", auth(member)))
                .andExpect(status().isNoContent());
        flushAndClear();
        assertThat(notificationRepository.findById(n.getId()).orElseThrow().getReadAt()).isEqualTo(readAt);
    }

    @Test
    void markRead_someoneElsesOrUnknown_returnsNotFound() throws Exception {
        Notification n = notify(other, NotificationType.SESSION_CREATED, Instant.now(), null);
        flushAndClear();

        mockMvc.perform(post("/api/v1/notifications/" + n.getId() + "/read").header("Authorization", auth(member)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/notifications/999999/read").header("Authorization", auth(member)))
                .andExpect(status().isNotFound());
        flushAndClear();
        assertThat(notificationRepository.findById(n.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    void markRead_bumpNotification_alsoDismissesTheBump() throws Exception {
        DailyAttendanceBump bump = bumpRepository.save(DailyAttendanceBump.builder()
                .daily(daily).user(member).bumpedBy(admin).bumpedAt(Instant.now()).build());
        Notification n = notify(member, NotificationType.DIARISTA_BUMPED, Instant.now(), null);
        flushAndClear();

        mockMvc.perform(post("/api/v1/notifications/" + n.getId() + "/read").header("Authorization", auth(member)))
                .andExpect(status().isNoContent());
        flushAndClear();

        assertThat(bumpRepository.findById(bump.getId()).orElseThrow().getSeenAt()).isNotNull();
    }

    // --- POST /api/v1/notifications/read-all ---

    @Test
    void markAllRead_onlyTheCallersAndTheirBumps() throws Exception {
        DailyAttendanceBump bump = bumpRepository.save(DailyAttendanceBump.builder()
                .daily(daily).user(member).bumpedBy(admin).bumpedAt(Instant.now()).build());
        Notification bumped = notify(member, NotificationType.DIARISTA_BUMPED, Instant.now(), null);
        Notification created = notify(member, NotificationType.SESSION_CREATED, Instant.now(), null);
        Notification others = notify(other, NotificationType.SESSION_CREATED, Instant.now(), null);
        flushAndClear();

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", auth(member)))
                .andExpect(status().isNoContent());
        flushAndClear();

        assertThat(notificationRepository.findById(bumped.getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(notificationRepository.findById(created.getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(notificationRepository.findById(others.getId()).orElseThrow().getReadAt()).isNull();
        assertThat(bumpRepository.findById(bump.getId()).orElseThrow().getSeenAt()).isNotNull();
    }

    // --- bump sync from the session side ---

    @Test
    void markBumpsSeen_alsoReadsTheBumpNotification() throws Exception {
        bumpRepository.save(DailyAttendanceBump.builder()
                .daily(daily).user(member).bumpedBy(admin).bumpedAt(Instant.now()).build());
        Notification bumped = notify(member, NotificationType.DIARISTA_BUMPED, Instant.now(), null);
        Notification created = notify(member, NotificationType.SESSION_CREATED, Instant.now(), null);
        flushAndClear();

        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/bumps/seen").header("Authorization", auth(member)))
                .andExpect(status().isNoContent());
        flushAndClear();

        assertThat(notificationRepository.findById(bumped.getId()).orElseThrow().getReadAt()).isNotNull();
        assertThat(notificationRepository.findById(created.getId()).orElseThrow().getReadAt()).isNull();
    }

    // --- end to end: one event per path ---

    private List<Notification> notificationsOf(User user) {
        flushAndClear();
        return notificationRepository.findPage(user.getId(), false, org.springframework.data.domain.PageRequest.of(0, 50));
    }

    @Test
    void mensalistaConfirmingIntoAFullSession_notifiesTheBumpedDiarista() throws Exception {
        pelada.setNumberOfTeams(1);
        pelada.setPlayersPerTeam(1);
        mensalistaRepository.save(PeladaMensalista.builder().pelada(pelada).user(admin).build());
        attendanceRepository.save(new DailyAttendance(daily, member, Instant.now()));
        flushAndClear();

        mockMvc.perform(post("/api/v1/dailies/" + daily.getId() + "/confirm").header("Authorization", auth(admin)))
                .andExpect(status().isOk());

        List<Notification> notifications = notificationsOf(member);
        assertThat(notifications).singleElement().satisfies(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.DIARISTA_BUMPED);
            assertThat(n.getActor().getId()).isEqualTo(admin.getId());
            assertThat(n.getDaily().getId()).isEqualTo(daily.getId());
        });
        assertThat(notificationsOf(admin)).isEmpty();
    }

    @Test
    void cancelingASession_notifiesTheConfirmedPlayersButNotTheAdmin() throws Exception {
        attendanceRepository.save(new DailyAttendance(daily, member, Instant.now()));
        attendanceRepository.save(new DailyAttendance(daily, admin, Instant.now()));
        flushAndClear();

        mockMvc.perform(put("/api/v1/dailies/" + daily.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", auth(admin))
                        .content(objectMapper.writeValueAsString(new UpdateDailyStatusRequestDTO(DailyStatus.CANCELED))))
                .andExpect(status().isOk());

        assertThat(notificationsOf(member)).extracting(Notification::getType).containsExactly(NotificationType.SESSION_CANCELED);
        assertThat(notificationsOf(admin)).isEmpty();
    }

    @Test
    void invitingAPlayer_notifiesThemWithoutMembership() throws Exception {
        mockMvc.perform(post("/api/v1/peladas/" + pelada.getId() + "/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", auth(admin))
                        .content(objectMapper.writeValueAsString(new InvitePlayerRequestDTO(other.getId()))))
                .andExpect(status().isOk());

        assertThat(notificationsOf(other)).singleElement().satisfies(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.PELADA_INVITATION);
            assertThat(n.getPelada().getId()).isEqualTo(pelada.getId());
            assertThat(n.getDaily()).isNull();
        });
    }

    // --- explicit deletes (H2 has no ON DELETE CASCADE) ---

    @Test
    void deletingADailyWithNotifications_works() throws Exception {
        notify(member, NotificationType.SESSION_CREATED, Instant.now(), null);
        flushAndClear();

        mockMvc.perform(delete("/api/v1/dailies/" + daily.getId()).header("Authorization", auth(admin)))
                .andExpect(status().isNoContent());

        assertThat(notificationsOf(member)).isEmpty();
    }

    @Test
    void deletingAPeladaWithNotifications_works() throws Exception {
        notify(member, NotificationType.SESSION_CREATED, Instant.now(), null);
        notificationRepository.save(Notification.builder().recipient(member).type(NotificationType.PELADA_INVITATION)
                .pelada(pelada).actor(admin).createdAt(Instant.now()).build());
        flushAndClear();

        mockMvc.perform(delete("/api/v1/peladas/" + pelada.getId()).header("Authorization", auth(admin)))
                .andExpect(status().isNoContent());

        assertThat(notificationsOf(member)).isEmpty();
    }

    // --- preferences ---

    private String preferencesBody(Object... categoryAndEnabled) throws Exception {
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (int i = 0; i < categoryAndEnabled.length; i += 2) {
            items.add(Map.of("category", categoryAndEnabled[i], "emailEnabled", categoryAndEnabled[i + 1]));
        }
        return objectMapper.writeValueAsString(items);
    }

    @Test
    void preferences_withoutRows_returnTheDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences").header("Authorization", auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.category == 'ATTENDANCE')].emailEnabled").value(true))
                .andExpect(jsonPath("$[?(@.category == 'MEMBERSHIP')].emailEnabled").value(true))
                .andExpect(jsonPath("$[?(@.category == 'SESSION')].emailEnabled").value(false))
                .andExpect(jsonPath("$[?(@.category == 'AWARDS')].emailEnabled").value(false));
    }

    @Test
    void updatePreferences_storesOnlyWhatDiffersFromTheDefault() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/preferences").header("Authorization", auth(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesBody("ATTENDANCE", false, "SESSION", true, "MEMBERSHIP", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.category == 'ATTENDANCE')].emailEnabled").value(false))
                .andExpect(jsonPath("$[?(@.category == 'SESSION')].emailEnabled").value(true));

        assertThat(preferenceRepository.findByIdUserId(member.getId()))
                .extracting(NotificationPreference::getCategory)
                .containsExactlyInAnyOrder(NotificationCategory.ATTENDANCE, NotificationCategory.SESSION);
        assertThat(preferenceRepository.findByIdUserId(admin.getId())).isEmpty();

        // back to the default → the row goes away
        mockMvc.perform(put("/api/v1/notifications/preferences").header("Authorization", auth(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesBody("ATTENDANCE", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.category == 'ATTENDANCE')].emailEnabled").value(true))
                .andExpect(jsonPath("$[?(@.category == 'SESSION')].emailEnabled").value(true));
        assertThat(preferenceRepository.findByIdUserId(member.getId()))
                .extracting(NotificationPreference::getCategory)
                .containsExactly(NotificationCategory.SESSION);
    }

    @Test
    void updatePreferences_invalidBodies_returnBadRequest() throws Exception {
        for (String body : List.of(
                "[]",
                preferencesBody("ATTENDANCE", false, "ATTENDANCE", true),
                "[{\"category\": null, \"emailEnabled\": true}]",
                "[{\"category\": \"ATTENDANCE\"}]",
                "[{\"category\": \"CHAT\", \"emailEnabled\": true}]")) {
            mockMvc.perform(put("/api/v1/notifications/preferences").header("Authorization", auth(member))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(preferenceRepository.findByIdUserId(member.getId())).isEmpty();
    }

    @Test
    void preferences_withoutToken_returnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences")).andExpect(status().isUnauthorized());
    }

    @Test
    void emailEnabled_appliesTheDefaultsAndTheRowsOfEachUser() {
        preferenceRepository.save(new NotificationPreference(member, NotificationCategory.ATTENDANCE, false));
        preferenceRepository.save(new NotificationPreference(member, NotificationCategory.AWARDS, true));
        flushAndClear();

        Map<Long, Map<NotificationCategory, Boolean>> enabled =
                preferenceService.emailEnabled(List.of(member.getId(), admin.getId()));

        assertThat(enabled.get(member.getId())).containsEntry(NotificationCategory.ATTENDANCE, false)
                .containsEntry(NotificationCategory.AWARDS, true)
                .containsEntry(NotificationCategory.MEMBERSHIP, true)
                .containsEntry(NotificationCategory.SESSION, false);
        assertThat(enabled.get(admin.getId())).containsEntry(NotificationCategory.ATTENDANCE, true)
                .containsEntry(NotificationCategory.AWARDS, false);
    }

    // --- public unsubscribe / resubscribe ---

    private static final AtomicInteger IP = new AtomicInteger();

    // A new client IP per request, so these tests never share a rate-limit bucket
    private static RequestPostProcessor freshIp() {
        int n = IP.incrementAndGet();
        return request -> {
            request.setRemoteAddr("10.1." + (n / 250) + "." + (n % 250 + 1));
            return request;
        };
    }

    private String tokenBody(String token) throws Exception {
        return objectMapper.writeValueAsString(Map.of("token", token));
    }

    private Map<NotificationCategory, Boolean> emailOf(User user) {
        flushAndClear();
        return preferenceService.emailEnabled(List.of(user.getId())).get(user.getId());
    }

    @Test
    void unsubscribe_withoutSession_turnsOnlyThatUsersCategoryOff() throws Exception {
        String token = unsubscribeTokens.create(member.getId(), NotificationCategory.ATTENDANCE);

        mockMvc.perform(post("/api/v1/notifications/unsubscribe").with(freshIp())
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("ATTENDANCE"))
                .andExpect(jsonPath("$.emailEnabled").value(false));

        assertThat(emailOf(member)).containsEntry(NotificationCategory.ATTENDANCE, false)
                .containsEntry(NotificationCategory.MEMBERSHIP, true);
        assertThat(emailOf(admin)).containsEntry(NotificationCategory.ATTENDANCE, true);
    }

    // The browser may still hold an expired or revoked session: the route ignores it instead of answering 401
    @Test
    void unsubscribe_withAStaleSessionHeader_stillWorks() throws Exception {
        String token = unsubscribeTokens.create(member.getId(), NotificationCategory.MEMBERSHIP);

        mockMvc.perform(post("/api/v1/notifications/unsubscribe").with(freshIp())
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody(token)))
                .andExpect(status().isOk());

        assertThat(emailOf(member)).containsEntry(NotificationCategory.MEMBERSHIP, false);
    }

    // RFC 8058: the mail provider POSTs "List-Unsubscribe=One-Click" to the header's URL, token in the query
    @Test
    void unsubscribe_oneClickFromTheMailProvider() throws Exception {
        String token = unsubscribeTokens.create(member.getId(), NotificationCategory.ATTENDANCE);

        mockMvc.perform(post("/api/v1/notifications/unsubscribe").with(freshIp()).queryParam("token", token)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("List-Unsubscribe=One-Click"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false));

        assertThat(emailOf(member)).containsEntry(NotificationCategory.ATTENDANCE, false);
    }

    @Test
    void resubscribe_turnsTheCategoryBackOn() throws Exception {
        String token = unsubscribeTokens.create(member.getId(), NotificationCategory.SESSION);

        mockMvc.perform(post("/api/v1/notifications/resubscribe").with(freshIp())
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("SESSION"))
                .andExpect(jsonPath("$.emailEnabled").value(true));

        assertThat(emailOf(member)).containsEntry(NotificationCategory.SESSION, true);
    }

    @Test
    void unsubscribe_invalidTokens_returnBadRequest() throws Exception {
        String valid = unsubscribeTokens.create(member.getId(), NotificationCategory.ATTENDANCE);
        String swapped = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString((member.getId() + ":SESSION").getBytes()) + "." + valid.split("\\.")[1];
        String unknownUser = unsubscribeTokens.create(999_999L, NotificationCategory.ATTENDANCE);

        for (String token : List.of(valid + "x", swapped, unknownUser, "garbage")) {
            mockMvc.perform(post("/api/v1/notifications/unsubscribe").with(freshIp())
                            .contentType(MediaType.APPLICATION_JSON).content(tokenBody(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Link inválido"));
        }
        mockMvc.perform(post("/api/v1/notifications/unsubscribe").with(freshIp())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\": \"\"}"))
                .andExpect(status().isBadRequest());

        assertThat(emailOf(member)).containsEntry(NotificationCategory.ATTENDANCE, true)
                .containsEntry(NotificationCategory.SESSION, false);
    }

    @Test
    void unsubscribe_isRateLimitedPerIp() throws Exception {
        String token = unsubscribeTokens.create(member.getId(), NotificationCategory.ATTENDANCE);
        RequestPostProcessor sameIp = freshIp();

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/notifications/unsubscribe").with(sameIp)
                            .contentType(MediaType.APPLICATION_JSON).content(tokenBody(token)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/notifications/unsubscribe").with(sameIp)
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody(token)))
                .andExpect(status().isTooManyRequests());
    }
}
