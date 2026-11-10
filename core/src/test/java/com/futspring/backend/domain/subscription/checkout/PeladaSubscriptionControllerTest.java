package com.futspring.backend.domain.subscription.checkout;

import com.futspring.backend.domain.notification.Notification;
import com.futspring.backend.domain.notification.NotificationRepository;
import com.futspring.backend.domain.notification.NotificationType;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.domain.subscription.PeladaSubscription;
import com.futspring.backend.domain.subscription.PeladaSubscriptionRepository;
import com.futspring.backend.domain.subscription.SubscriptionKind;
import com.futspring.backend.domain.subscription.SubscriptionPlan;
import com.futspring.backend.domain.subscription.SubscriptionPlans;
import com.futspring.backend.domain.subscription.SubscriptionService;
import com.futspring.backend.domain.subscription.billing.SubscriptionEvent;
import com.futspring.backend.domain.subscription.billing.SubscriptionEventRepository;
import com.futspring.backend.domain.subscription.billing.SubscriptionEventType;
import com.futspring.backend.domain.subscription.billing.SubscriptionPayment;
import com.futspring.backend.domain.subscription.billing.SubscriptionPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PeladaSubscriptionControllerTest extends BaseIntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");

    @MockitoBean Clock clock;
    @Autowired UserRepository users;
    @Autowired PeladaRepository peladas;
    @Autowired PeladaSubscriptionRepository subscriptions;
    @Autowired SubscriptionPaymentRepository payments;
    @Autowired SubscriptionEventRepository events;
    @Autowired NotificationRepository notifications;

    User creator;
    User admin;
    User member;
    User outsider;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        creator = user("criador");
        admin = user("admin");
        member = user("membro");
        outsider = user("de-fora");
    }

    @Test
    void creator_seesState_pricesWithTheirPixCodes_andPaymentsNotVoided() throws Exception {
        Pelada pelada = pelada(SubscriptionKind.PAID, NOW.plus(Duration.ofDays(10)), Set.of());
        payment(pelada, "19.90", LocalDate.of(2026, 10, 4), false);
        payment(pelada, "199.00", LocalDate.of(2026, 10, 20), true);

        MvcResult result = as(creator, get(path(pelada))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.validUntil").value(NOW.plus(Duration.ofDays(10)).toString()))
                .andExpect(jsonPath("$.pixRecipient").value("Futspring Teste Recebedor"))
                .andExpect(jsonPath("$.plans[0].plan").value("MONTHLY"))
                .andExpect(jsonPath("$.plans[0].amount").value("19.90"))
                .andExpect(jsonPath("$.plans[1].plan").value("ANNUAL"))
                .andExpect(jsonPath("$.plans[1].amount").value("199.00"))
                .andExpect(jsonPath("$.plans[0].pixCode").value(startsWith("000201")))
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.payments[0].amount").value("19.90"))
                .andExpect(jsonPath("$.payments[0].paidOn").value("2026-10-04"))
                .andReturn();

        String code = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("plans").get(1).get("pixCode").asString();
        assertThat(code).contains("5406199.00").contains("FUT" + pelada.getId()).contains("6009SAO PAULO");
        String body = code.substring(0, code.length() - 4);
        assertThat(code).endsWith(PixBrCode.crc16(body));
    }

    @Test
    void aLargeGroup_getsTheLargeGroupPrices() throws Exception {
        Set<User> extra = IntStream.range(0, SubscriptionPlans.LARGE_GROUP_MEMBERS)
                .mapToObj(i -> user("jogador" + i)).collect(Collectors.toSet());
        Pelada pelada = pelada(SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(3)), extra);

        as(creator, get(path(pelada))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.plans[0].amount").value("29.90"))
                .andExpect(jsonPath("$.plans[1].amount").value("299.00"));
    }

    @Test
    void anyoneButTheCreator_gets403_andAnUnknownPelada404() throws Exception {
        Pelada pelada = pelada(SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(3)), Set.of());
        for (User caller : List.of(admin, member, outsider)) {
            as(caller, get(path(pelada))).andExpect(status().isForbidden());
            as(caller, claim(pelada, "MONTHLY")).andExpect(status().isForbidden());
        }
        as(creator, get("/api/v1/peladas/999999/subscription")).andExpect(status().isNotFound());
        mockMvc.perform(get(path(pelada))).andExpect(status().isUnauthorized());
        assertThat(subscriptions.findByPeladaId(pelada.getId()).orElseThrow().getPixClaimedAt()).isNull();
    }

    @Test
    void claim_recordsWhenAndThePlan_repeatingChangesNothing_anotherPlanKeepsTheDate() throws Exception {
        Pelada pelada = pelada(SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(3)), Set.of());

        as(creator, claim(pelada, "ANNUAL")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pixClaimedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.claimedPlan").value("ANNUAL"));
        assertThat(claimEvents(pelada)).isEqualTo(1);

        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        as(creator, claim(pelada, "ANNUAL")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pixClaimedAt").value(NOW.toString()));
        assertThat(claimEvents(pelada)).isEqualTo(1);

        as(creator, claim(pelada, "MONTHLY")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pixClaimedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.claimedPlan").value("MONTHLY"));
        assertThat(claimEvents(pelada)).isEqualTo(2);
        SubscriptionEvent last = events.findByPeladaIdOrderByCreatedAtDescIdDesc(pelada.getId(), PageRequest.of(0, 1)).getFirst();
        assertThat(last.getActor().getId()).isEqualTo(creator.getId());
        assertThat(last.getKindBefore()).isEqualTo(SubscriptionKind.TRIAL);
        assertThat(last.getKindAfter()).isEqualTo(SubscriptionKind.TRIAL);
    }

    @Test
    void onlyTheFirstClaim_tellsThePlatformAdmins() throws Exception {
        outsider.setPlatformAdmin(true);
        users.save(outsider);
        User deletedAdmin = user("ex-admin");
        deletedAdmin.setPlatformAdmin(true);
        deletedAdmin.setDeletedAt(NOW);
        users.save(deletedAdmin);
        Pelada pelada = pelada(SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(3)), Set.of());

        as(creator, claim(pelada, "ANNUAL")).andExpect(status().isOk());
        as(creator, claim(pelada, "ANNUAL")).andExpect(status().isOk());
        as(creator, claim(pelada, "MONTHLY")).andExpect(status().isOk());

        List<Notification> claimed = notifications.findAll().stream()
                .filter(n -> n.getType() == NotificationType.SUBSCRIPTION_PIX_CLAIMED).toList();
        assertThat(claimed).singleElement().satisfies(n -> {
            assertThat(n.getRecipient().getId()).isEqualTo(outsider.getId());
            assertThat(n.getActor().getId()).isEqualTo(creator.getId());
            assertThat(n.getPelada().getId()).isEqualTo(pelada.getId());
        });
    }

    @Test
    void aReadOnlyPelada_canStillClaim_anExemptOneCant() throws Exception {
        Pelada pending = pelada(SubscriptionKind.PENDING, null, Set.of());
        as(creator, claim(pending, "MONTHLY")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.claimedPlan").value("MONTHLY"));

        Pelada exempt = pelada(SubscriptionKind.EXEMPT, null, Set.of());
        as(creator, claim(exempt, "MONTHLY")).andExpect(status().isConflict());
        assertThat(subscriptions.findByPeladaId(exempt.getId()).orElseThrow().getPixClaimedAt()).isNull();
    }

    @Test
    void claim_withoutPlan_is400() throws Exception {
        Pelada pelada = pelada(SubscriptionKind.TRIAL, NOW.plus(Duration.ofDays(3)), Set.of());
        as(creator, post(path(pelada) + "/pix-claim").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.plan").exists());
        as(creator, get(path(pelada))).andExpect(jsonPath("$.pixClaimedAt").value(nullValue()));
    }

    private long claimEvents(Pelada pelada) {
        return events.findByPeladaIdOrderByCreatedAtDescIdDesc(pelada.getId(), PageRequest.of(0, 50)).stream()
                .filter(e -> e.getType() == SubscriptionEventType.PIX_CLAIMED).count();
    }

    private static String path(Pelada pelada) {
        return "/api/v1/peladas/" + pelada.getId() + "/subscription";
    }

    private static MockHttpServletRequestBuilder claim(Pelada pelada, String plan) {
        return post(path(pelada) + "/pix-claim").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plan\":\"" + plan + "\"}");
    }

    private ResultActions as(User caller, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearerToken(caller.getId(), caller.getEmail())));
    }

    private User user(String name) {
        return users.save(User.builder().username(name).email(name + "@assinatura.test").password("hash").build());
    }

    private Pelada pelada(SubscriptionKind kind, Instant validUntil, Set<User> extraMembers) {
        Set<User> members = new HashSet<>(Set.of(creator, admin, member));
        members.addAll(extraMembers);
        Pelada pelada = peladas.save(Pelada.builder().name("Pelada").dayOfWeek("FRIDAY").timeOfDay("18:00")
                .duration(2f).creator(creator).members(members)
                .admins(new HashSet<>(Set.of(creator, admin))).build());
        subscriptions.save(new PeladaSubscription(pelada, kind, validUntil, NOW));
        return pelada;
    }

    private void payment(Pelada pelada, String amount, LocalDate paidOn, boolean voided) {
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setPeladaId(pelada.getId());
        payment.setPeladaName(pelada.getName());
        payment.setPayer(creator);
        payment.setPayerName(creator.getUsername());
        payment.setAmount(new BigDecimal(amount));
        payment.setPaidOn(paidOn);
        payment.setPlan(SubscriptionPlan.MONTHLY);
        payment.setPeriodStart(NOW);
        payment.setPeriodEnd(SubscriptionService.endOfDay(paidOn.plusMonths(1)));
        payment.setRecordedBy(creator);
        payment.setCreatedAt(NOW);
        if (voided) {
            payment.setVoidedAt(NOW);
            payment.setVoidReason("engano");
        }
        payments.save(payment);
    }
}
