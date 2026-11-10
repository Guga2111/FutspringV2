package com.futspring.backend.domain.user;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.attendance.DailyAttendance;
import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.daily.team.Team;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.team.TeamRepository;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.finance.account.FinanceAccount;
import com.futspring.backend.domain.finance.account.FinanceAccountRepository;
import com.futspring.backend.domain.finance.FinanceCollectionService;
import com.futspring.backend.domain.finance.FinanceLedgerService;
import com.futspring.backend.domain.finance.account.FinanceManager;
import com.futspring.backend.domain.finance.account.FinanceManagerRepository;
import com.futspring.backend.domain.finance.collection.FinancePersonRepository;
import com.futspring.backend.domain.finance.FinanceReadService;
import com.futspring.backend.domain.finance.dto.FinanceRequests;
import com.futspring.backend.domain.finance.FinanceTypes.Classification;
import com.futspring.backend.domain.finance.FinanceTypes.PaymentMethod;
import com.futspring.backend.domain.notification.Notification;
import com.futspring.backend.domain.notification.NotificationRepository;
import com.futspring.backend.domain.notification.NotificationType;
import com.futspring.backend.domain.notification.webpush.PushSubscription;
import com.futspring.backend.domain.notification.webpush.PushSubscriptionRepository;
import com.futspring.backend.domain.pelada.invitation.InvitationStatus;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.invitation.model.PeladaInvitation;
import com.futspring.backend.domain.pelada.invitation.model.PeladaInvitationRepository;
import com.futspring.backend.domain.pelada.member.PeladaMensalista;
import com.futspring.backend.domain.pelada.member.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStars;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStarsRepository;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.stats.RankingService;
import com.futspring.backend.domain.stats.dto.RankingDTO;
import com.futspring.backend.domain.stats.model.Ranking;
import com.futspring.backend.domain.stats.model.RankingRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.support.QueryCounter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Account settings routes: change password, deletion blockers and account deletion (anonymization). */
class AccountSettingsTest extends BaseIntegrationTest {

    private static final String PASSWORD = "senha-atual-123";
    // Every request gets its own client IP, so the rate limit (10/min per IP and route) only bites where tested
    private static final AtomicInteger IP = new AtomicInteger();

    @Autowired UserRepository users;
    @Autowired PeladaRepository peladas;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AccountDeletionService deletion;
    @Autowired FinanceManagerRepository managers;
    @Autowired FinanceAccountRepository accounts;
    @Autowired FinancePersonRepository people;
    @Autowired FinanceCollectionService collections;
    @Autowired FinanceLedgerService ledger;
    @Autowired FinanceReadService financeReads;
    @Autowired DailyRepository dailies;
    @Autowired DailyAttendanceRepository attendance;
    @Autowired TeamRepository teams;
    @Autowired RankingRepository rankings;
    @Autowired RankingService rankingService;
    @Autowired PeladaPlayerStarsRepository stars;
    @Autowired PeladaMensalistaRepository mensalistas;
    @Autowired PeladaInvitationRepository invitations;
    @Autowired NotificationRepository notifications;
    @Autowired FileUploadService fileUploadService;
    @Autowired QueryCounter queryCounter;
    @Autowired PushSubscriptionRepository pushSubscriptions;
    @Autowired EntityManager em;
    @Value("${app.uploads.dir}") String uploadsDir;

    User creator;
    User player;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        creator = user("Criador");
        player = user("Jogador");
        pelada = pelada("Pelada do Fut", creator, player);
    }

    // --- POST /users/me/password ---

    @Test
    void changePassword_wrongCurrentPassword_isAFieldError() throws Exception {
        mockMvc.perform(as(post("/api/v1/users/me/password"), player)
                        .content(json(Map.of("currentPassword", "errada", "newPassword", "nova-senha-123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("Senha atual incorreta"));
        assertThat(passwordEncoder.matches(PASSWORD, users.findById(player.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void changePassword_shortNewPassword_is400() throws Exception {
        mockMvc.perform(as(post("/api/v1/users/me/password"), player)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "curta"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    void changePassword_returnsAWorkingTokenAndRevokesTheOldOne() throws Exception {
        String body = mockMvc.perform(as(post("/api/v1/users/me/password"), player)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "nova-senha-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(player.getId()))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("token").asString();

        mockMvc.perform(get("/api/v1/users/" + player.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/" + player.getId())
                        .header("Authorization", bearerToken(player.getId(), player.getEmail())))
                .andExpect(status().isUnauthorized());
        User saved = users.findById(player.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("nova-senha-123", saved.getPassword())).isTrue();
        assertThat(saved.getTokenVersion()).isEqualTo(1);
    }

    // The sessions are revoked, so the old devices stop receiving Web Push too (this one subscribes again on open)
    @Test
    void changePassword_dropsEveryPushSubscriptionOfTheCaller() throws Exception {
        subscribe(player, "player-phone");
        subscribe(creator, "creator-phone");

        mockMvc.perform(as(post("/api/v1/users/me/password"), player)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "nova-senha-123"))))
                .andExpect(status().isOk());

        assertThat(pushSubscriptions.findByUserIdIn(List.of(player.getId()))).isEmpty();
        assertThat(pushSubscriptions.findByUserIdIn(List.of(creator.getId()))).hasSize(1);
    }

    @Test
    void delete_dropsEveryPushSubscription() throws Exception {
        subscribe(player, "deleted-phone");

        mockMvc.perform(as(post("/api/v1/users/me/delete"), player).content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(pushSubscriptions.findByUserIdIn(List.of(player.getId()))).isEmpty();
    }

    private void subscribe(User user, String device) {
        pushSubscriptions.save(new PushSubscription(user, "https://fcm.googleapis.com/fcm/send/" + device,
                "BAAA", "AAAA", java.time.Instant.now()));
    }

    @Test
    void changePassword_isRateLimited() throws Exception {
        String ip = "10.99.0.1";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/users/me/password").with(r -> { r.setRemoteAddr(ip); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", bearerToken(player.getId(), player.getEmail()))
                    .content(json(Map.of("currentPassword", "errada", "newPassword", "nova-senha-123"))));
        }
        mockMvc.perform(post("/api/v1/users/me/password").with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearerToken(player.getId(), player.getEmail()))
                        .content(json(Map.of("currentPassword", "errada", "newPassword", "nova-senha-123"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void changePassword_withoutToken_is401() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/password").with(r -> { r.setRemoteAddr(nextIp()); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "nova-senha-123"))))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /users/me/deletion-blockers ---

    @Test
    void blockers_creatorAndSoleFinanceManager() throws Exception {
        Pelada other = pelada("Pelada de Quarta", player, creator);
        appoint(other, creator);

        mockMvc.perform(as(get("/api/v1/users/me/deletion-blockers"), creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("CREATOR"))
                .andExpect(jsonPath("$[0].peladaId").value(pelada.getId()))
                .andExpect(jsonPath("$[0].peladaName").value("Pelada do Fut"))
                .andExpect(jsonPath("$[1].type").value("SOLE_FINANCE_MANAGER"))
                .andExpect(jsonPath("$[1].peladaId").value(other.getId()));
    }

    @Test
    void blockers_managerWithAnotherManager_isNothing() throws Exception {
        appoint(pelada, creator);
        appoint(pelada, player);
        mockMvc.perform(as(get("/api/v1/users/me/deletion-blockers"), player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- POST /users/me/delete ---

    @Test
    void delete_wrongPassword_isAFieldErrorAndChangesNothing() throws Exception {
        mockMvc.perform(as(post("/api/v1/users/me/delete"), player).content(json(Map.of("password", "errada"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Senha incorreta"));
        assertThat(users.findById(player.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void delete_creator_is409() throws Exception {
        mockMvc.perform(as(post("/api/v1/users/me/delete"), creator).content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Transfira ou exclua a Pelada do Fut antes de excluir a conta"));
        assertThat(users.findById(creator.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void delete_soleFinanceManager_is409() throws Exception {
        appoint(pelada, player);
        mockMvc.perform(as(post("/api/v1/users/me/delete"), player).content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Designe outro responsável financeiro da Pelada do Fut antes de excluir a conta"));
        assertThat(peladas.existsByIdAndMembers_Id(pelada.getId(), player.getId())).isTrue();
    }

    @Test
    void delete_anonymizesTheAccountAndKeepsOtherPeoplesHistory() throws Exception {
        String oldEmail = player.getEmail();
        User other = user("Outro");
        addMember(pelada, other);
        // history: a finished session with player on a team, and rankings for both
        Daily finished = dailies.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.of(2026, 10, 1))
                .dailyTime("19:00").status(DailyStatus.FINISHED).isFinished(true).build());
        teams.save(Team.builder().daily(finished).name("Time 1").players(new HashSet<>(Set.of(player, other))).build());
        rankings.save(Ranking.builder().pelada(pelada).user(player).goals(5).matchesPlayed(3).build());
        rankings.save(Ranking.builder().pelada(pelada).user(other).goals(2).assists(1).matchesPlayed(3).wins(2).build());
        // present: an upcoming session where player is confirmed and on a team
        Daily upcoming = upcomingSessionWith(pelada, player);
        // pelada data: admin, stars, mensalista, a notification and a pending invitation elsewhere
        pelada.getAdmins().add(player);
        stars.save(PeladaPlayerStars.builder().pelada(pelada).user(player).stars(5).build());
        mensalistas.save(PeladaMensalista.builder().pelada(pelada).user(player).build());
        notifications.save(Notification.builder().recipient(player).type(NotificationType.SESSION_CREATED)
                .pelada(pelada).createdAt(Instant.now()).build());
        Pelada elsewhere = pelada("Pelada de Domingo", other);
        PeladaInvitation invitation = invitations.save(PeladaInvitation.builder().pelada(elsewhere).recipient(player)
                .sender(other).createdAt(Instant.now()).expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .status(InvitationStatus.PENDING).build());
        // money: player is co-responsável, was charged and paid
        appoint(pelada, creator);
        appoint(pelada, player);
        Long listId = collections.collection(pelada.getId(), null, new FinanceRequests.Collection(command(pelada),
                "Mensalidade", Classification.MONTHLY, "", null, null,
                List.of(new FinanceRequests.Component("Campo", "40.00")), null, null, null), creator.getEmail())
                .detail().collection().id();
        Long chargeId = collections.charge(pelada.getId(), listId, null, false, new FinanceRequests.Charge(command(pelada),
                player.getId(), null, null, List.of(new FinanceRequests.Component("Campo", "40.00")), "0.00", null),
                creator.getEmail()).detail().charges().getLast().id();
        collections.transition(pelada.getId(), listId, "PUBLISH", command(pelada), creator.getEmail());
        ledger.receive(pelada.getId(), listId, chargeId,
                new FinanceRequests.Receipt(command(pelada), "40.00", null, PaymentMethod.PIX), creator.getEmail());
        long revision = revision(pelada);
        String balance = financeReads.overview(pelada.getId(), creator.getEmail(), 0, 50).balance();
        List<RankingDTO> before = rankingService.getRanking(pelada.getId(), other.getEmail());
        em.flush();

        mockMvc.perform(as(post("/api/v1/users/me/delete"), player).content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();

        User deleted = users.findById(player.getId()).orElseThrow();
        assertThat(deleted.getUsername()).isEqualTo("Ex-jogador");
        assertThat(deleted.getEmail()).isEqualTo("deleted-" + player.getId() + "@invalid");
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getTokenVersion()).isEqualTo(1);
        assertThat(passwordEncoder.matches(PASSWORD, deleted.getPassword())).isFalse();

        // login with the old e-mail, the old token, the profile and the search
        mockMvc.perform(post("/api/v1/auth/login").with(r -> { r.setRemoteAddr(nextIp()); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", oldEmail, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me/deletion-blockers")
                        .header("Authorization", bearerToken(player.getId(), oldEmail)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get("/api/v1/users/" + player.getId()), other)).andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/v1/users/" + player.getId() + "/stats"), other)).andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/v1/users/search").param("q", "Ex-jogador"), other))
                .andExpect(jsonPath("$.length()").value(0));

        // memberships and the upcoming session
        assertThat(peladas.existsByIdAndMembers_Id(pelada.getId(), player.getId())).isFalse();
        assertThat(peladas.existsByIdAndAdmins_Id(pelada.getId(), player.getId())).isFalse();
        assertThat(stars.findByPeladaAndUser(pelada, player)).isEmpty();
        assertThat(mensalistas.existsByPeladaAndUser(pelada, player)).isFalse();
        assertThat(attendance.findByDailyInAndUserIn(List.of(upcoming), List.of(player))).isEmpty();
        assertThat(teams.findByDailyInWithPlayers(List.of(upcoming)))
                .singleElement().satisfies(team -> assertThat(team.getPlayers()).isEmpty());
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(player.getId())).isZero();
        assertThat(invitations.findById(invitation.getId()).orElseThrow().getStatus()).isEqualTo(InvitationStatus.CANCELED);

        // history: ranking without them and with the same numbers for the others, a finished session showing them
        assertThat(rankingService.getRanking(pelada.getId(), other.getEmail()))
                .containsExactlyElementsOf(before.stream().filter(r -> !r.userId().equals(player.getId())).toList());
        mockMvc.perform(as(get("/api/v1/dailies/" + finished.getId()), other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].players[?(@.id == " + player.getId() + ")].username").value("Ex-jogador"))
                .andExpect(jsonPath("$.teams[0].players[?(@.id == " + player.getId() + ")].isDeleted").value(true))
                .andExpect(jsonPath("$.teams[0].players[?(@.id == " + other.getId() + ")].isDeleted").value(false));

        // money: same balance, the charge is kept under the anonymous name, the delegation is gone and audited
        assertThat(financeReads.overview(pelada.getId(), creator.getEmail(), 0, 50).balance()).isEqualTo(balance);
        assertThat(people.findByPeladaIdAndUserId(pelada.getId(), player.getId()).orElseThrow().getName()).isEqualTo("Ex-jogador");
        assertThat(managers.findByPeladaId(pelada.getId())).extracting(FinanceManager::getUserId).containsExactly(creator.getId());
        assertThat(revision(pelada)).isEqualTo(revision + 1);

        // the e-mail can be registered again
        mockMvc.perform(post("/api/v1/auth/register").with(r -> { r.setRemoteAddr(nextIp()); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "Novo" + UUID.randomUUID().toString().substring(0, 8),
                                "email", oldEmail, "password", "outra-senha-123",
                                "termsVersion", com.futspring.backend.domain.auth.LegalTerms.CURRENT_VERSION))))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_readsAConstantNumberOfQueriesWhateverTheNumberOfPeladas() {
        deletionReads(1); // warm-up: one-off context loads don't count
        assertThat(deletionReads(5)).isEqualTo(deletionReads(1));
    }

    @Test
    void delete_imageIsRemovedOnlyAfterTheCommit_andARollbackChangesNothing() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User owner = user("Dono" + suffix);
        User leaving = user("Saindo" + suffix);
        Pelada committed = pelada("Pelada " + suffix, owner, leaving);
        String image = fileUploadService.uploadImage(
                new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3}));
        leaving.setImage(image);
        users.save(leaving);
        Path file = Path.of(uploadsDir).resolve(image);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        try {
            TestTransaction.start();
            deletion.deleteAccount(PASSWORD, leaving.getEmail());
            assertThat(Files.exists(file)).isTrue();
            TestTransaction.flagForRollback();
            TestTransaction.end();
            assertThat(Files.exists(file)).isTrue();

            TestTransaction.start();
            User untouched = users.findById(leaving.getId()).orElseThrow();
            assertThat(untouched.getDeletedAt()).isNull();
            assertThat(untouched.getImage()).isEqualTo(image);
            assertThat(peladas.existsByIdAndMembers_Id(committed.getId(), leaving.getId())).isTrue();
            deletion.deleteAccount(PASSWORD, leaving.getEmail());
            assertThat(Files.exists(file)).isTrue();
            TestTransaction.flagForCommit();
            TestTransaction.end();
            assertThat(Files.exists(file)).isFalse();
        } finally {
            // committed rows must not leak into the other tests
            TestTransaction.start();
            peladas.deleteById(committed.getId());
            users.deleteAllById(List.of(owner.getId(), leaving.getId()));
            TestTransaction.flagForCommit();
            TestTransaction.end();
            TestTransaction.start();
        }
    }

    // A user in `count` peladas, each with what a deletion clears: admin, stars, mensalista, an upcoming
    // session with attendance and a team, co-delegation with the creator and a financial identity
    private long deletionReads(int count) {
        User owner = user("Dono");
        User leaving = user("Saindo");
        for (int i = 0; i < count; i++) {
            Pelada p = pelada("Pelada " + i, owner, leaving);
            p.getAdmins().add(leaving);
            stars.save(PeladaPlayerStars.builder().pelada(p).user(leaving).stars(4).build());
            mensalistas.save(PeladaMensalista.builder().pelada(p).user(leaving).build());
            upcomingSessionWith(p, leaving);
            appoint(p, owner);
            appoint(p, leaving);
            var person = new com.futspring.backend.domain.finance.collection.FinancePerson();
            person.setPeladaId(p.getId());
            person.setUserId(leaving.getId());
            person.setName(leaving.getUsername());
            people.save(person);
        }
        em.flush();
        em.clear();
        long reads = queryCounter.reads(() -> deletion.deleteAccount(PASSWORD, leaving.getEmail()));
        em.flush();
        assertThat(peladas.findIdsByMemberId(leaving.getId())).isEmpty();
        return reads;
    }

    private Daily upcomingSessionWith(Pelada p, User user) {
        Daily daily = dailies.save(Daily.builder().pelada(p).dailyDate(LocalDate.of(2026, 12, 1)).dailyTime("19:00").build());
        attendance.save(new DailyAttendance(daily, user, Instant.now()));
        teams.save(Team.builder().daily(daily).name("Time 1").players(new HashSet<>(Set.of(user))).build());
        return daily;
    }

    private User user(String name) {
        return users.save(User.builder().username(name + UUID.randomUUID().toString().substring(0, 8))
                .email(UUID.randomUUID() + "@account.test").password(passwordEncoder.encode(PASSWORD)).build());
    }

    private Pelada pelada(String name, User owner, User... others) {
        Set<User> members = new HashSet<>(Set.of(others));
        members.add(owner);
        Pelada saved = peladas.save(Pelada.builder().name(name).dayOfWeek("SATURDAY").timeOfDay("19:00").duration(1f)
                .creator(owner).members(members).admins(new HashSet<>(Set.of(owner))).build());
        em.flush();
        return saved;
    }

    private void addMember(Pelada p, User user) {
        p.getMembers().add(user);
        peladas.save(p);
        em.flush();
    }

    private void appoint(Pelada p, User user) {
        var manager = new FinanceManager();
        manager.setPeladaId(p.getId());
        manager.setUserId(user.getId());
        managers.saveAndFlush(manager);
    }

    private long revision(Pelada p) {
        return accounts.findByPeladaId(p.getId()).map(FinanceAccount::getRevision).orElse(0L);
    }

    private FinanceRequests.Command command(Pelada p) {
        return new FinanceRequests.Command(revision(p), UUID.randomUUID(), "Correção justificada");
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        String ip = nextIp();
        return request.with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(user.getId(), user.getEmail()));
    }

    private static String nextIp() {
        int n = IP.incrementAndGet();
        return "10.1." + (n / 250) + "." + (n % 250 + 1);
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }
}
