package com.futspring.backend.domain.user;

import com.futspring.backend.domain.chat.Message;
import com.futspring.backend.domain.chat.MessageRepository;
import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.attendance.DailyAttendance;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.finance.account.FinanceAccount;
import com.futspring.backend.domain.finance.account.FinanceAccountRepository;
import com.futspring.backend.domain.finance.FinanceCollectionService;
import com.futspring.backend.domain.finance.FinanceLedgerService;
import com.futspring.backend.domain.finance.account.FinanceManager;
import com.futspring.backend.domain.finance.account.FinanceManagerRepository;
import com.futspring.backend.domain.finance.FinanceTypes.Classification;
import com.futspring.backend.domain.finance.FinanceTypes.PaymentMethod;
import com.futspring.backend.domain.finance.dto.FinanceRequests;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.member.PeladaMensalista;
import com.futspring.backend.domain.pelada.member.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStars;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStarsRepository;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.support.QueryCounter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /users/me/export: the caller's own data, nothing about other people, constant reads. */
class AccountExportTest extends BaseIntegrationTest {

    private static final AtomicInteger IP = new AtomicInteger();

    @Autowired UserRepository users;
    @Autowired PeladaRepository peladas;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AccountExportService exportService;
    @Autowired PeladaPlayerStarsRepository stars;
    @Autowired PeladaMensalistaRepository mensalistas;
    @Autowired DailyRepository dailies;
    @Autowired DailyAttendanceRepository attendance;
    @Autowired MessageRepository messages;
    @Autowired FinanceManagerRepository managers;
    @Autowired FinanceAccountRepository accounts;
    @Autowired FinanceCollectionService collections;
    @Autowired FinanceLedgerService ledger;
    @Autowired QueryCounter queryCounter;
    @Autowired EntityManager em;

    User creator;
    User player;
    User other;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        creator = user("Criador");
        player = user("Jogador");
        other = user("Outro");
        pelada = pelada("Pelada do Fut", creator, player, other);
    }

    @Test
    void withoutToken_is401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheCallersOwnDataAsADownload() throws Exception {
        stars.save(PeladaPlayerStars.builder().pelada(pelada).user(player).stars(5).build());
        mensalistas.save(PeladaMensalista.builder().pelada(pelada).user(player).build());
        Daily daily = dailies.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.of(2026, 12, 1)).dailyTime("19:00").build());
        attendance.save(new DailyAttendance(daily, player, Instant.parse("2026-11-30T10:00:00Z")));
        attendance.save(new DailyAttendance(daily, other, Instant.parse("2026-11-30T11:00:00Z")));
        messages.save(Message.builder().pelada(pelada).sender(player).content("Bora!").build());
        messages.save(Message.builder().pelada(pelada).sender(other).content("Mensagem de outro").build());
        financeFixture();
        em.flush();

        mockMvc.perform(as(get("/api/v1/users/me/export"), player))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("futspring-meus-dados-")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.formatVersion").value(1))
                .andExpect(jsonPath("$.account.id").value(player.getId()))
                .andExpect(jsonPath("$.account.email").value(player.getEmail()))
                .andExpect(jsonPath("$.account.password").doesNotExist())
                .andExpect(jsonPath("$.peladas", hasSize(1)))
                .andExpect(jsonPath("$.peladas[0].peladaName").value("Pelada do Fut"))
                .andExpect(jsonPath("$.peladas[0].isCreator").value(false))
                .andExpect(jsonPath("$.peladas[0].isMensalista").value(true))
                .andExpect(jsonPath("$.peladas[0].stars").value(5))
                .andExpect(jsonPath("$.attendance", hasSize(1)))
                .andExpect(jsonPath("$.attendance[0].dailyId").value(daily.getId()))
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].content").value("Bora!"))
                // only the published list's charge; the draft and the other member's charge stay out
                .andExpect(jsonPath("$.finance.charges", hasSize(1)))
                .andExpect(jsonPath("$.finance.charges[0].listName").value("Mensalidade"))
                .andExpect(jsonPath("$.finance.charges[0].peladaName").value("Pelada do Fut"))
                .andExpect(jsonPath("$.finance.charges[0].amountDue").value("40.00"))
                .andExpect(jsonPath("$.finance.charges[0].received").value("15.00"))
                .andExpect(jsonPath("$.subscriptions", hasSize(0)))
                .andExpect(jsonPath("$.notificationPreferences", hasSize(5)))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain(other.getEmail(), creator.getEmail(), "Mensagem de outro");
                });
    }

    @Test
    void creatorGetsTheSubscriptionOfThePeladasTheyCreated() throws Exception {
        Pelada own = peladaThroughTheApi(creator);
        mockMvc.perform(as(get("/api/v1/users/me/export"), creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions", hasSize(1)))
                .andExpect(jsonPath("$.subscriptions[0].peladaId").value(own.getId()))
                .andExpect(jsonPath("$.subscriptions[0].kind").value("TRIAL"))
                .andExpect(jsonPath("$.subscriptions[0].status").value("TRIAL"));
    }

    @Test
    void isRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(fromIp(get("/api/v1/users/me/export"), player, "10.3.0.1")).andExpect(status().isOk());
        }
        mockMvc.perform(fromIp(get("/api/v1/users/me/export"), player, "10.3.0.1")).andExpect(status().isTooManyRequests());
    }

    @Test
    void sendsAConstantNumberOfStatementsWhateverTheNumberOfPeladas() {
        assertThat(exportStatements(1)).isEqualTo(exportStatements(5));
    }

    // A user in `count` peladas, each with stars, a mensalista row, a confirmed session and a chat message
    private long exportStatements(int count) {
        User owner = user("Dono");
        User member = user("Membro");
        for (int i = 0; i < count; i++) {
            Pelada p = pelada("Pelada " + i, owner, member);
            p.getAdmins().add(member);
            stars.save(PeladaPlayerStars.builder().pelada(p).user(member).stars(4).build());
            mensalistas.save(PeladaMensalista.builder().pelada(p).user(member).build());
            Daily daily = dailies.save(Daily.builder().pelada(p).dailyDate(LocalDate.of(2026, 12, 1)).dailyTime("19:00").build());
            attendance.save(new DailyAttendance(daily, member, Instant.now()));
            messages.save(Message.builder().pelada(p).sender(member).content("Oi " + i).build());
        }
        em.flush();
        em.clear();
        exportService.export(member.getEmail());
        em.clear();
        // statements, not reads: reads also counts every hydrated row (sessions, messages), which grows with the data
        return queryCounter.statements(() -> exportService.export(member.getEmail()));
    }

    // A published list with the player's charge (partly paid) and the other member's, plus a draft for the player
    private void financeFixture() {
        appoint(creator);
        Long listId = collections.collection(pelada.getId(), null, collection("Mensalidade"), creator.getEmail())
                .detail().collection().id();
        Long chargeId = collections.charge(pelada.getId(), listId, null, false, charge(player), creator.getEmail())
                .detail().charges().getLast().id();
        collections.charge(pelada.getId(), listId, null, false, charge(other), creator.getEmail());
        collections.transition(pelada.getId(), listId, "PUBLISH", command(), creator.getEmail());
        ledger.receive(pelada.getId(), listId, chargeId,
                new FinanceRequests.Receipt(command(), "15.00", null, PaymentMethod.PIX), creator.getEmail());

        Long draftId = collections.collection(pelada.getId(), null, collection("Rascunho"), creator.getEmail())
                .detail().collection().id();
        collections.charge(pelada.getId(), draftId, null, false, charge(player), creator.getEmail());
    }

    private FinanceRequests.Collection collection(String name) {
        return new FinanceRequests.Collection(command(), name, Classification.MONTHLY, "", null, null,
                List.of(new FinanceRequests.Component("Campo", "40.00")), null, null, null);
    }

    private FinanceRequests.Charge charge(User user) {
        return new FinanceRequests.Charge(command(), user.getId(), null, null,
                List.of(new FinanceRequests.Component("Campo", "40.00")), "0.00", null);
    }

    private Pelada peladaThroughTheApi(User owner) throws Exception {
        String body = mockMvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/peladas"), owner)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("name", "Minha Pelada",
                                "dayOfWeek", "SATURDAY", "timeOfDay", "19:00", "duration", 1.5))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return peladas.findById(objectMapper.readTree(body).get("id").asLong()).orElseThrow();
    }

    private User user(String name) {
        return users.save(User.builder().username(name + UUID.randomUUID().toString().substring(0, 8))
                .email(UUID.randomUUID() + "@export.test").password(passwordEncoder.encode("senha-123")).build());
    }

    private Pelada pelada(String name, User owner, User... others) {
        Set<User> members = new HashSet<>(Set.of(others));
        members.add(owner);
        Pelada saved = peladas.save(Pelada.builder().name(name).dayOfWeek("SATURDAY").timeOfDay("19:00").duration(1f)
                .creator(owner).members(members).admins(new HashSet<>(Set.of(owner))).build());
        em.flush();
        return saved;
    }

    private void appoint(User user) {
        var manager = new FinanceManager();
        manager.setPeladaId(pelada.getId());
        manager.setUserId(user.getId());
        managers.saveAndFlush(manager);
    }

    private FinanceRequests.Command command() {
        long revision = accounts.findByPeladaId(pelada.getId()).map(FinanceAccount::getRevision).orElse(0L);
        return new FinanceRequests.Command(revision, UUID.randomUUID(), "Correção justificada");
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        int n = IP.incrementAndGet();
        return fromIp(request, user, "10.2." + (n / 250) + "." + (n % 250 + 1));
    }

    private MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder request, User user, String ip) {
        return request.with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", bearerToken(user.getId(), user.getEmail()));
    }
}
