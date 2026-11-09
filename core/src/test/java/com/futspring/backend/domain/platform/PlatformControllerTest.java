package com.futspring.backend.domain.platform;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.subscription.PeladaSubscription;
import com.futspring.backend.domain.subscription.PeladaSubscriptionRepository;
import com.futspring.backend.domain.subscription.SubscriptionEvent;
import com.futspring.backend.domain.subscription.SubscriptionEventRepository;
import com.futspring.backend.domain.subscription.SubscriptionEventType;
import com.futspring.backend.domain.subscription.SubscriptionKind;
import com.futspring.backend.domain.subscription.SubscriptionPayment;
import com.futspring.backend.domain.subscription.SubscriptionPaymentRepository;
import com.futspring.backend.domain.subscription.SubscriptionPlan;
import com.futspring.backend.domain.subscription.SubscriptionService;
import com.futspring.backend.domain.subscription.SubscriptionState;
import com.futspring.backend.domain.subscription.SubscriptionStatus;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.support.QueryCounter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformControllerTest extends BaseIntegrationTest {

    // Wednesday 12:00 in São Paulo
    static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");

    @MockitoBean Clock clock;
    @Autowired UserRepository users;
    @Autowired PeladaRepository peladas;
    @Autowired DailyRepository dailies;
    @Autowired PeladaSubscriptionRepository subscriptions;
    @Autowired SubscriptionPaymentRepository payments;
    @Autowired SubscriptionEventRepository events;
    @Autowired QueryCounter queryCounter;
    @Autowired EntityManager entityManager;

    User operator;
    User creator;
    User member;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        operator = user("operador");
        operator.setPlatformAdmin(true);
        creator = user("criador");
        member = user("membro");
    }

    // ---- access ----

    @Test
    void everyRoute_answers404ToAnyoneButThePlatformAdmin() throws Exception {
        Pelada pelada = pelada("Pelada", SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(5)));
        SubscriptionPayment payment = payment(pelada, "19.90", LocalDate.of(2026, 11, 1));
        List<MockHttpServletRequestBuilder> routes = List.of(
                get("/api/v1/platform/overview"),
                get("/api/v1/platform/peladas"),
                get("/api/v1/platform/peladas/" + pelada.getId()),
                json(post("/api/v1/platform/peladas/" + pelada.getId() + "/payments"),
                        "{\"plan\":\"MONTHLY\",\"amount\":\"19.90\",\"paidOn\":\"2026-11-04\"}"),
                json(post("/api/v1/platform/peladas/" + pelada.getId() + "/subscription"),
                        "{\"action\":\"EXEMPT\",\"note\":\"amigo\"}"),
                json(post("/api/v1/platform/payments/" + payment.getId() + "/void"), "{\"reason\":\"engano\"}"),
                get("/api/v1/platform/payments"),
                get("/api/v1/platform/users"));
        for (User caller : List.of(creator, member)) {
            for (MockHttpServletRequestBuilder route : routes) {
                mockMvc.perform(route.header("Authorization", bearerToken(caller.getId(), caller.getEmail())))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.message").value("Recurso não encontrado"));
            }
        }
        assertThat(subscriptions.findByPeladaId(pelada.getId()).orElseThrow().getKind()).isEqualTo(SubscriptionKind.TRIAL);
        assertThat(payments.findById(payment.getId()).orElseThrow().isVoided()).isFalse();
        mockMvc.perform(get("/api/v1/platform/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void isPlatformAdmin_onlyOnTheCallersOwnUser() throws Exception {
        asOperator(get("/api/v1/users/" + operator.getId())).andExpect(jsonPath("$.isPlatformAdmin").value(true));
        mockMvc.perform(get("/api/v1/users/" + creator.getId())
                        .header("Authorization", bearerToken(creator.getId(), creator.getEmail())))
                .andExpect(jsonPath("$.isPlatformAdmin").value(false));
        // someone else's profile never says it
        mockMvc.perform(get("/api/v1/users/" + operator.getId())
                        .header("Authorization", bearerToken(creator.getId(), creator.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isPlatformAdmin").doesNotExist());
    }

    // ---- payments ----

    @Test
    void payment_duringARunningTrial_startsWhereTheTrialEnds() throws Exception {
        Instant trialEnd = Instant.parse("2026-11-20T18:30:00Z");
        Pelada pelada = pelada("Em teste", SubscriptionKind.TRIAL, trialEnd);
        PeladaSubscription subscription = subscriptions.findByPeladaId(pelada.getId()).orElseThrow();
        subscription.setPixClaimedAt(NOW.minusSeconds(3600));
        subscription.setClaimedPlan(SubscriptionPlan.MONTHLY);

        Instant expectedEnd = Instant.parse("2026-12-21T02:59:59Z"); // 20/12 23:59:59 in São Paulo
        asOperator(json(post("/api/v1/platform/peladas/" + pelada.getId() + "/payments"),
                "{\"plan\":\"MONTHLY\",\"amount\":\"19.90\",\"paidOn\":\"2026-11-04\",\"note\":\"Pix\"}"))
                .andExpect(jsonPath("$.subscription.kind").value("PAID"))
                .andExpect(jsonPath("$.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.subscription.validUntil").value(expectedEnd.toString()))
                .andExpect(jsonPath("$.subscription.plan").value("MONTHLY"))
                .andExpect(jsonPath("$.subscription.pixClaimedAt").value(nullValue()))
                .andExpect(jsonPath("$.payments[0].amount").value("19.90"))
                .andExpect(jsonPath("$.payments[0].periodStart").value(trialEnd.toString()))
                .andExpect(jsonPath("$.payments[0].periodEnd").value(expectedEnd.toString()))
                .andExpect(jsonPath("$.payments[0].payerName").value("criador"))
                .andExpect(jsonPath("$.payments[0].peladaName").value("Em teste"))
                .andExpect(jsonPath("$.payments[0].recordedBy.username").value("operador"))
                .andExpect(jsonPath("$.events[0].type").value("PAYMENT_RECORDED"))
                .andExpect(jsonPath("$.events[0].actor.username").value("operador"))
                .andExpect(jsonPath("$.events[0].kindBefore").value("TRIAL"))
                .andExpect(jsonPath("$.events[0].validUntilBefore").value(trialEnd.toString()))
                .andExpect(jsonPath("$.events[0].validUntilAfter").value(expectedEnd.toString()))
                .andExpect(jsonPath("$.events[0].note").value("Pix"));
    }

    @Test
    void annualPayment_afterTheEnd_startsNow() throws Exception {
        Pelada pelada = pelada("Vencida", SubscriptionKind.PAID, NOW.minus(Duration.ofDays(3)));

        asOperator(json(post("/api/v1/platform/peladas/" + pelada.getId() + "/payments"),
                "{\"plan\":\"ANNUAL\",\"amount\":\"199.00\",\"paidOn\":\"2026-11-03\"}"))
                .andExpect(jsonPath("$.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.subscription.validUntil").value("2027-11-05T02:59:59Z"))
                .andExpect(jsonPath("$.payments[0].periodStart").value(NOW.toString()));
    }

    @Test
    void payment_rejectsAFutureDateAndAZeroOrBadlyWrittenAmount() throws Exception {
        Pelada pelada = pelada("P", SubscriptionKind.PENDING, null);
        String path = "/api/v1/platform/peladas/" + pelada.getId() + "/payments";

        asOperatorExpecting(json(post(path), "{\"plan\":\"MONTHLY\",\"amount\":\"19.90\",\"paidOn\":\"2026-11-05\"}"), 400)
                .andExpect(jsonPath("$.errors.paidOn").exists());
        asOperatorExpecting(json(post(path), "{\"plan\":\"MONTHLY\",\"amount\":\"0.00\",\"paidOn\":\"2026-11-04\"}"), 400)
                .andExpect(jsonPath("$.errors.amount").exists());
        asOperatorExpecting(json(post(path), "{\"plan\":\"MONTHLY\",\"amount\":\"19.9\",\"paidOn\":\"2026-11-04\"}"), 400)
                .andExpect(jsonPath("$.errors.amount").exists());
        asOperatorExpecting(json(post("/api/v1/platform/peladas/999999/payments"),
                "{\"plan\":\"MONTHLY\",\"amount\":\"19.90\",\"paidOn\":\"2026-11-04\"}"), 404);
        assertThat(payments.count()).isZero();
    }

    @Test
    void void_leavesTheTotals_keepsTheValidity_andHappensOnce() throws Exception {
        Pelada pelada = pelada("Paga", SubscriptionKind.PAID, NOW.plus(Duration.ofDays(20)));
        SubscriptionPayment kept = payment(pelada, "19.90", LocalDate.of(2026, 11, 1));
        SubscriptionPayment voided = payment(pelada, "199.00", LocalDate.of(2026, 11, 2));

        asOperator(json(post("/api/v1/platform/payments/" + voided.getId() + "/void"), "{\"reason\":\"Pix devolvido\"}"))
                .andExpect(jsonPath("$.voidedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.voidReason").value("Pix devolvido"));
        asOperatorExpecting(json(post("/api/v1/platform/payments/" + voided.getId() + "/void"), "{\"reason\":\"de novo\"}"), 409);
        asOperatorExpecting(json(post("/api/v1/platform/payments/" + kept.getId() + "/void"), "{\"reason\":\" \"}"), 400);

        asOperator(get("/api/v1/platform/payments?from=2026-11-01&to=2026-11-30"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.sum").value("19.90"))
                .andExpect(jsonPath("$.items[0].id").value(voided.getId()));
        asOperator(get("/api/v1/platform/overview"))
                .andExpect(jsonPath("$.revenueThisMonth").value("19.90"));
        asOperator(get("/api/v1/platform/peladas/" + pelada.getId()))
                .andExpect(jsonPath("$.subscription.validUntil").value(NOW.plus(Duration.ofDays(20)).toString()))
                .andExpect(jsonPath("$.events[0].type").value("PAYMENT_VOIDED"))
                .andExpect(jsonPath("$.events[0].note").value("Pix devolvido"));
    }

    @Test
    void paymentsList_filtersInclusivelyByPaidOn() throws Exception {
        Pelada pelada = pelada("P", SubscriptionKind.PAID, NOW.plus(Duration.ofDays(20)));
        payment(pelada, "10.00", LocalDate.of(2026, 10, 31));
        payment(pelada, "20.00", LocalDate.of(2026, 11, 1));
        payment(pelada, "30.00", LocalDate.of(2026, 11, 3));

        asOperator(get("/api/v1/platform/payments?from=2026-11-01&to=2026-11-03"))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.sum").value("50.00"));
        asOperator(get("/api/v1/platform/payments")).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.sum").value("60.00"));
        asOperatorExpecting(get("/api/v1/platform/payments?from=2026-11-03&to=2026-11-01"), 400)
                .andExpect(jsonPath("$.message").value("Período inválido"));
    }

    // ---- subscription actions ----

    @Test
    void actions_changeTheSubscriptionAndRecordAnEvent() throws Exception {
        Pelada pelada = pelada("Ações", SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(2)));
        String path = "/api/v1/platform/peladas/" + pelada.getId() + "/subscription";

        asOperator(json(post(path), "{\"action\":\"EXTEND_DAYS\",\"days\":10,\"note\":\"Mais tempo\"}"))
                .andExpect(jsonPath("$.subscription.kind").value("TRIAL"))
                .andExpect(jsonPath("$.subscription.validUntil").value(NOW.plus(Duration.ofDays(12)).toString()))
                .andExpect(jsonPath("$.events[0].type").value("DAYS_EXTENDED"));
        asOperator(json(post(path), "{\"action\":\"SET_VALID_UNTIL\",\"validUntil\":\"2026-12-07\",\"note\":\"Combinado\"}"))
                .andExpect(jsonPath("$.subscription.kind").value("PAID"))
                .andExpect(jsonPath("$.subscription.validUntil").value("2026-12-08T02:59:59Z"))
                .andExpect(jsonPath("$.events[0].type").value("VALID_UNTIL_SET"))
                .andExpect(jsonPath("$.events[0].kindBefore").value("TRIAL"))
                .andExpect(jsonPath("$.events[0].kindAfter").value("PAID"));
        asOperator(json(post(path), "{\"action\":\"SET_FOUNDER\",\"founder\":true}"))
                .andExpect(jsonPath("$.subscription.founder").value(true))
                .andExpect(jsonPath("$.events[0].type").value("FOUNDER_CHANGED"));
        asOperator(json(post(path), "{\"action\":\"CANCEL\",\"note\":\"Pediu\"}"))
                .andExpect(jsonPath("$.subscription.status").value("READ_ONLY"))
                .andExpect(jsonPath("$.subscription.validUntil").value(nullValue()));
        // a canceled subscription has no end to extend
        asOperatorExpecting(json(post(path), "{\"action\":\"EXTEND_DAYS\",\"days\":3,\"note\":\"x\"}"), 409);
        asOperator(json(post(path), "{\"action\":\"EXEMPT\",\"note\":\"Pelada do dono\"}"))
                .andExpect(jsonPath("$.subscription.status").value("EXEMPT"))
                .andExpect(jsonPath("$.subscription.writable").value(true))
                .andExpect(jsonPath("$.events", hasSize(5)))
                .andExpect(jsonPath("$.events[0].type").value("EXEMPTED"))
                .andExpect(jsonPath("$.events[0].actor.id").value(operator.getId()));
    }

    @Test
    void actions_requireANoteAndTheirOwnField() throws Exception {
        Pelada pelada = pelada("Validação", SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(2)));
        String path = "/api/v1/platform/peladas/" + pelada.getId() + "/subscription";

        asOperatorExpecting(json(post(path), "{\"action\":\"CANCEL\",\"note\":\"  \"}"), 400)
                .andExpect(jsonPath("$.errors.note").exists());
        asOperatorExpecting(json(post(path), "{\"action\":\"EXTEND_DAYS\",\"note\":\"x\"}"), 400)
                .andExpect(jsonPath("$.errors.days").exists());
        asOperatorExpecting(json(post(path), "{\"action\":\"EXTEND_DAYS\",\"days\":0,\"note\":\"x\"}"), 400);
        asOperatorExpecting(json(post(path), "{\"action\":\"SET_VALID_UNTIL\",\"note\":\"x\"}"), 400)
                .andExpect(jsonPath("$.errors.validUntil").exists());
        asOperatorExpecting(json(post(path), "{\"action\":\"SET_FOUNDER\"}"), 400)
                .andExpect(jsonPath("$.errors.founder").exists());
        assertThat(events.findAll()).extracting(SubscriptionEvent::getType).doesNotContain(SubscriptionEventType.CANCELED);
    }

    // ---- lists ----

    @ParameterizedTest
    @EnumSource(SubscriptionStatus.class)
    void stateFilter_agreesWithTheEffectiveStateAtEveryEdge(SubscriptionStatus state) throws Exception {
        Map<String, PeladaSubscription> edges = edgeSubscriptions();
        Set<String> expected = edges.entrySet().stream()
                .filter(e -> SubscriptionState.of(e.getValue(), NOW) == state)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        assertThat(expected).isNotEmpty();

        asOperator(get("/api/v1/platform/peladas?size=50&state=" + state))
                .andExpect(jsonPath("$.total").value(expected.size()))
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder(expected.toArray())))
                .andExpect(jsonPath("$.items[*].status", containsInAnyOrder(
                        expected.stream().map(name -> state.name()).toArray())));
        asOperator(get("/api/v1/platform/overview"))
                .andExpect(jsonPath("$.counts." + state).value(expected.size()));
    }

    @Test
    void peladasList_searchesAndCarriesMembersAndTheLastSession() throws Exception {
        Pelada pelada = pelada("Fut de Quinta", SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(5)));
        pelada("Outra", SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(5)));
        daily(pelada, LocalDate.of(2026, 10, 29), DailyStatus.FINISHED);
        daily(pelada, LocalDate.of(2026, 11, 5), DailyStatus.SCHEDULED);

        asOperator(get("/api/v1/platform/peladas?q=QUINTA"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(pelada.getId()))
                .andExpect(jsonPath("$.items[0].creator.username").value("criador"))
                .andExpect(jsonPath("$.items[0].memberCount").value(2))
                .andExpect(jsonPath("$.items[0].lastSessionDate").value("2026-10-29"));
        asOperator(get("/api/v1/platform/peladas?q=criador@")).andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void overview_listsWhatNeedsTheAdminAndTheRevenue() throws Exception {
        Pelada ending = pelada("Acabando", SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(6)));
        pelada("Longe", SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(8)));
        Pelada claimed = pelada("Pagou", SubscriptionKind.PENDING, null);
        subscriptions.findByPeladaId(claimed.getId()).orElseThrow().setPixClaimedAt(NOW.minusSeconds(60));
        Pelada monthly = pelada("Mensal", SubscriptionKind.PAID, NOW.plus(Duration.ofDays(20)));
        Pelada annual = pelada("Anual", SubscriptionKind.PAID, NOW.plus(Duration.ofDays(300)));
        payment(monthly, "19.90", LocalDate.of(2026, 10, 21), SubscriptionPlan.MONTHLY, NOW.minus(Duration.ofDays(10)), NOW.plus(Duration.ofDays(20)));
        payment(annual, "240.00", LocalDate.of(2025, 12, 1), SubscriptionPlan.ANNUAL, NOW.minus(Duration.ofDays(60)), NOW.plus(Duration.ofDays(300)));
        payment(annual, "50.00", LocalDate.of(2025, 11, 30), SubscriptionPlan.MONTHLY, NOW.minus(Duration.ofDays(90)), NOW.minus(Duration.ofDays(60)));

        asOperator(get("/api/v1/platform/overview"))
                .andExpect(jsonPath("$.trialsEndingSoon", hasSize(1)))
                .andExpect(jsonPath("$.trialsEndingSoon[0].id").value(ending.getId()))
                .andExpect(jsonPath("$.pixClaimed[0].id").value(claimed.getId()))
                .andExpect(jsonPath("$.revenueByMonth", hasSize(12)))
                .andExpect(jsonPath("$.revenueByMonth[0].month").value("2025-12"))
                .andExpect(jsonPath("$.revenueByMonth[0].total").value("240.00"))
                .andExpect(jsonPath("$.revenueByMonth[10].total").value("19.90"))
                .andExpect(jsonPath("$.revenueByMonth[11].month").value("2026-11"))
                .andExpect(jsonPath("$.revenueThisMonth").value("0.00"))
                // 19.90 + 240.00 / 12
                .andExpect(jsonPath("$.recurringEstimate").value("39.90"))
                .andExpect(jsonPath("$.users.total").value(3))
                .andExpect(jsonPath("$.users.last30Days").value(3));
    }

    @Test
    void usersList_showsEveryAccountWithItsPeladas() throws Exception {
        Pelada pelada = pelada("Do criador", SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(5)));
        member.setDeletedAt(NOW);

        asOperator(get("/api/v1/platform/users?q=criador"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].email").value("criador@platform.test"))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].peladasCreated[0].id").value(pelada.getId()))
                .andExpect(jsonPath("$.items[0].peladasCreated[0].name").value("Do criador"))
                .andExpect(jsonPath("$.items[0].memberOf").value(1));
        asOperator(get("/api/v1/platform/users?q=membro"))
                .andExpect(jsonPath("$.items[0].isDeleted").value(true))
                .andExpect(jsonPath("$.items[0].peladasCreated", hasSize(0)));
        asOperator(get("/api/v1/platform/users")).andExpect(jsonPath("$.total").value(3));
    }

    // ---- query counts ----

    @Test
    void overviewAndPeladas_runAConstantNumberOfStatements() {
        createPeladas(2);
        long fewOverview = statements(() -> readService().overview(operator.getEmail()));
        long fewPeladas = statements(() -> readService().peladas(operator.getEmail(), null, null, 0, 50));
        createPeladas(18);
        long manyOverview = statements(() -> readService().overview(operator.getEmail()));
        long manyPeladas = statements(() -> readService().peladas(operator.getEmail(), null, null, 0, 50));

        assertThat(readService().peladas(operator.getEmail(), null, null, 0, 50).items()).hasSize(20);
        assertThat(manyOverview).isEqualTo(fewOverview);
        assertThat(manyPeladas).isEqualTo(fewPeladas);
    }

    @Autowired PlatformReadService platformReadService;

    private PlatformReadService readService() {
        return platformReadService;
    }

    private void createPeladas(int count) {
        for (int i = 0; i < count; i++) {
            Pelada pelada = pelada("Lote " + peladas.count(), SubscriptionKind.PAID, NOW.plus(Duration.ofDays(5)));
            daily(pelada, LocalDate.of(2026, 10, 1), DailyStatus.FINISHED);
            payment(pelada, "19.90", LocalDate.of(2026, 10, 6));
        }
    }

    private long statements(Runnable block) {
        entityManager.flush();
        entityManager.clear();
        block.run(); // warm-up
        entityManager.clear();
        return queryCounter.statements(block);
    }

    // One pelada per edge of the state table, named after it
    private Map<String, PeladaSubscription> edgeSubscriptions() {
        Map<String, PeladaSubscription> edges = new java.util.LinkedHashMap<>();
        edges.put("trial-running", sub("trial-running", SubscriptionKind.TRIAL, NOW.plusSeconds(1)));
        edges.put("trial-ends-now", sub("trial-ends-now", SubscriptionKind.TRIAL, NOW));
        edges.put("paid-running", sub("paid-running", SubscriptionKind.PAID, NOW.plusSeconds(1)));
        edges.put("paid-ends-now", sub("paid-ends-now", SubscriptionKind.PAID, NOW));
        edges.put("grace-last-second", sub("grace-last-second", SubscriptionKind.PAID, NOW.minus(SubscriptionState.GRACE).plusSeconds(1)));
        edges.put("grace-over", sub("grace-over", SubscriptionKind.PAID, NOW.minus(SubscriptionState.GRACE)));
        edges.put("canceled", sub("canceled", SubscriptionKind.CANCELED, null));
        edges.put("pending", sub("pending", SubscriptionKind.PENDING, null));
        edges.put("exempt", sub("exempt", SubscriptionKind.EXEMPT, null));
        return edges;
    }

    private PeladaSubscription sub(String name, SubscriptionKind kind, Instant validUntil) {
        return subscriptions.findByPeladaId(pelada(name, kind, validUntil).getId()).orElseThrow();
    }

    private User user(String name) {
        return users.save(User.builder().username(name).email(name + "@platform.test").password("hash").build());
    }

    private Pelada pelada(String name, SubscriptionKind kind, Instant validUntil) {
        Pelada pelada = peladas.save(Pelada.builder().name(name).dayOfWeek("FRIDAY").timeOfDay("18:00")
                .duration(2f).creator(creator).members(new HashSet<>(Set.of(creator, member)))
                .admins(new HashSet<>(Set.of(creator))).build());
        subscriptions.save(new PeladaSubscription(pelada, kind, validUntil, NOW));
        return pelada;
    }

    private void daily(Pelada pelada, LocalDate date, DailyStatus status) {
        dailies.save(Daily.builder().pelada(pelada).dailyDate(date).dailyTime("18:00").status(status).build());
    }

    private SubscriptionPayment payment(Pelada pelada, String amount, LocalDate paidOn) {
        return payment(pelada, amount, paidOn, SubscriptionPlan.MONTHLY, NOW, SubscriptionService.endOfDay(paidOn.plusMonths(1)));
    }

    private SubscriptionPayment payment(Pelada pelada, String amount, LocalDate paidOn, SubscriptionPlan plan,
                                        Instant start, Instant end) {
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setPeladaId(pelada.getId());
        payment.setPeladaName(pelada.getName());
        payment.setPayer(creator);
        payment.setPayerName(creator.getUsername());
        payment.setAmount(new BigDecimal(amount));
        payment.setPaidOn(paidOn);
        payment.setPlan(plan);
        payment.setPeriodStart(start);
        payment.setPeriodEnd(end);
        payment.setRecordedBy(operator);
        payment.setCreatedAt(NOW);
        return payments.save(payment);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions asOperator(MockHttpServletRequestBuilder request) throws Exception {
        return asOperatorExpecting(request, 200);
    }

    private ResultActions asOperatorExpecting(MockHttpServletRequestBuilder request, int status) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearerToken(operator.getId(), operator.getEmail())))
                .andExpect(status().is(status));
    }
}
