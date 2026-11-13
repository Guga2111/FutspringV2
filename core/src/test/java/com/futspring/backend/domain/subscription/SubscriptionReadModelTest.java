package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.pelada.PeladaService;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.support.QueryCounter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The subscription's effective state reaches the client on the pelada list, the pelada detail and the session detail
class SubscriptionReadModelTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private DailyRepository dailyRepository;
    @Autowired private PeladaSubscriptionRepository subscriptionRepository;
    @Autowired private PeladaService peladaService;
    @Autowired private QueryCounter queryCounter;
    @Autowired private EntityManager entityManager;

    private User creator;
    private User member;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH).email("rm-creator@test.com").username("rmcreator").password("hash").build());
        member = userRepository.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH).email("rm-member@test.com").username("rmmember").password("hash").build());
    }

    @Test
    void trial_isWritableEverywhere() throws Exception {
        Instant validUntil = Instant.now().plus(Duration.ofDays(20));
        Pelada pelada = pelada("Em teste", SubscriptionKind.TRIAL, validUntil);
        Daily daily = daily(pelada);

        expectSubscription(asMember(get("/api/v1/peladas/my")), "$[0].subscription", "TRIAL", true)
                .andExpect(jsonPath("$[0].subscription.validUntil").value(validUntil.toString()));
        expectSubscription(asMember(get("/api/v1/peladas/" + pelada.getId())), "$.subscription", "TRIAL", true);
        expectSubscription(asMember(get("/api/v1/dailies/" + daily.getId())), "$.subscription", "TRIAL", true)
                .andExpect(jsonPath("$.creatorId").value(creator.getId()));
    }

    @Test
    void expiredTrial_isReadOnly() throws Exception {
        Pelada pelada = pelada("Vencida", SubscriptionKind.TRIAL, Instant.now().minusSeconds(60));
        Daily daily = daily(pelada);

        expectSubscription(asMember(get("/api/v1/peladas/my")), "$[0].subscription", "READ_ONLY", false);
        expectSubscription(asMember(get("/api/v1/peladas/" + pelada.getId())), "$.subscription", "READ_ONLY", false);
        expectSubscription(asMember(get("/api/v1/dailies/" + daily.getId())), "$.subscription", "READ_ONLY", false);
    }

    @Test
    void paidInItsGrace_isPastDueAndWritable() throws Exception {
        Pelada pelada = pelada("Em atraso", SubscriptionKind.PAID, Instant.now().minus(Duration.ofDays(2)));

        expectSubscription(asMember(get("/api/v1/peladas/" + pelada.getId())), "$.subscription", "PAST_DUE", true);
    }

    @Test
    void pending_isReadOnlyWithoutValidUntil() throws Exception {
        Pelada pelada = pelada("Pendente", SubscriptionKind.PENDING, null);

        expectSubscription(asMember(get("/api/v1/peladas/my")), "$[0].subscription", "PENDING", false)
                .andExpect(jsonPath("$[0].subscription.validUntil").value(nullValue()));
    }

    @Test
    void withoutARow_theSubscriptionIsNull() throws Exception {
        Pelada pelada = pelada("Sem linha", null, null);
        Daily daily = daily(pelada);

        asMember(get("/api/v1/peladas/my")).andExpect(jsonPath("$[0].subscription").value(nullValue()));
        asMember(get("/api/v1/peladas/" + pelada.getId())).andExpect(jsonPath("$.subscription").value(nullValue()));
        asMember(get("/api/v1/dailies/" + daily.getId())).andExpect(jsonPath("$.subscription").value(nullValue()));
    }

    @Test
    void myPeladas_runsAConstantNumberOfStatements() {
        for (int i = 0; i < 2; i++) daily(pelada("Pequena " + i, SubscriptionKind.TRIAL, Instant.now().plus(Duration.ofDays(5))));
        long few = statementsOfMyPeladas();
        for (int i = 0; i < 18; i++) daily(pelada("Grande " + i, SubscriptionKind.PAID, Instant.now().plus(Duration.ofDays(5))));
        long many = statementsOfMyPeladas();

        assertThat(peladaService.getMyPeladas(member.getEmail())).hasSize(20)
                .allSatisfy(p -> assertThat(p.subscription()).isNotNull());
        assertThat(many).isEqualTo(few);
    }

    private long statementsOfMyPeladas() {
        entityManager.flush();
        entityManager.clear();
        peladaService.getMyPeladas(member.getEmail()); // warm-up
        entityManager.clear();
        // statements, not reads: reads also counts every pelada row hydrated by the list query
        return queryCounter.statements(() -> peladaService.getMyPeladas(member.getEmail()));
    }

    private Pelada pelada(String name, SubscriptionKind kind, Instant validUntil) {
        Pelada pelada = peladaRepository.save(Pelada.builder().name(name).dayOfWeek("FRIDAY").timeOfDay("18:00")
                .duration(2f).creator(creator).members(new HashSet<>(Set.of(creator, member)))
                .admins(new HashSet<>(Set.of(creator))).numberOfTeams(2).playersPerTeam(5).build());
        if (kind != null) {
            subscriptionRepository.save(new PeladaSubscription(pelada, kind, validUntil, Instant.now()));
        }
        return pelada;
    }

    private Daily daily(Pelada pelada) {
        return dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().plusDays(3))
                .dailyTime("18:00").status(DailyStatus.SCHEDULED).build());
    }

    private ResultActions asMember(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk());
    }

    private static ResultActions expectSubscription(ResultActions result, String path, String status, boolean writable)
            throws Exception {
        return result.andExpect(jsonPath(path + ".status").value(status))
                .andExpect(jsonPath(path + ".writable").value(writable));
    }
}
