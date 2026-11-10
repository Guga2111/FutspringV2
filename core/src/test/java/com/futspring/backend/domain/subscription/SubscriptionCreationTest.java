package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.domain.subscription.billing.SubscriptionEventRepository;
import com.futspring.backend.domain.subscription.billing.SubscriptionEventType;
import com.futspring.backend.domain.subscription.billing.SubscriptionPayment;
import com.futspring.backend.domain.subscription.billing.SubscriptionPaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// A pelada is born TRIAL (the account's one trial) or PENDING; the subscription follows a transfer and its payments
// outlive the pelada
class SubscriptionCreationTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private PeladaSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionEventRepository eventRepository;
    @Autowired private SubscriptionPaymentRepository paymentRepository;
    @Autowired private EntityManager entityManager;

    private User ana;
    private User bia;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(User.builder().email("ana@sub.test").username("ana").password("hash").build());
        bia = userRepository.save(User.builder().email("bia@sub.test").username("bia").password("hash").build());
    }

    @Test
    void firstPelada_isATrialOf30Days_andMarksTheTrialUsed() throws Exception {
        Instant before = Instant.now();
        long id = create(ana, "Primeira");
        reload();

        PeladaSubscription subscription = subscriptionRepository.findByPeladaId(id).orElseThrow();
        assertThat(subscription.getKind()).isEqualTo(SubscriptionKind.TRIAL);
        assertThat(subscription.getValidUntil()).isCloseTo(before.plus(Duration.ofDays(30)), within(Duration.ofMinutes(1)));
        assertThat(SubscriptionState.of(subscription, Instant.now())).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(userRepository.findById(ana.getId()).orElseThrow().getTrialUsedAt()).isNotNull();

        assertThat(eventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getPeladaId()).isEqualTo(id);
            assertThat(event.getType()).isEqualTo(SubscriptionEventType.CREATED);
            assertThat(event.getActor().getId()).isEqualTo(ana.getId());
            assertThat(event.getKindBefore()).isNull();
            assertThat(event.getKindAfter()).isEqualTo(SubscriptionKind.TRIAL);
            assertThat(event.getValidUntilAfter()).isEqualTo(subscription.getValidUntil());
        });
    }

    @Test
    void secondPelada_isPending() throws Exception {
        create(ana, "Primeira");
        long second = create(ana, "Segunda");
        reload();

        PeladaSubscription subscription = subscriptionRepository.findByPeladaId(second).orElseThrow();
        assertThat(subscription.getKind()).isEqualTo(SubscriptionKind.PENDING);
        assertThat(subscription.getValidUntil()).isNull();
        assertThat(SubscriptionState.of(subscription, Instant.now())).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    void transfer_keepsTheSubscription_andGivesNoTrialToEitherSide() throws Exception {
        long id = create(ana, "Transferida");
        Pelada pelada = peladaRepository.findById(id).orElseThrow();
        pelada.getMembers().add(bia);
        pelada.getAdmins().add(bia);
        peladaRepository.save(pelada);
        reload();
        PeladaSubscription before = subscriptionRepository.findByPeladaId(id).orElseThrow();

        mockMvc.perform(put("/api/v1/peladas/" + id + "/creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearerToken(ana.getId(), ana.getEmail()))
                        .content("{\"userId\":" + bia.getId() + "}"))
                .andExpect(status().isOk());
        reload();

        PeladaSubscription after = subscriptionRepository.findByPeladaId(id).orElseThrow();
        assertThat(after.getKind()).isEqualTo(SubscriptionKind.TRIAL);
        assertThat(after.getValidUntil()).isEqualTo(before.getValidUntil());
        // receiving a pelada doesn't use the new creator's trial…
        assertThat(userRepository.findById(bia.getId()).orElseThrow().getTrialUsedAt()).isNull();
        assertThat(eventRepository.findAll()).hasSize(1);

        // …and giving it away doesn't give the old creator another one
        long anaSecond = create(ana, "Outra da Ana");
        long biaFirst = create(bia, "Primeira da Bia");
        reload();
        assertThat(subscriptionRepository.findByPeladaId(anaSecond).orElseThrow().getKind()).isEqualTo(SubscriptionKind.PENDING);
        assertThat(subscriptionRepository.findByPeladaId(biaFirst).orElseThrow().getKind()).isEqualTo(SubscriptionKind.TRIAL);
    }

    @Test
    void deletingThePelada_dropsSubscriptionAndHistory_butKeepsPayments() throws Exception {
        long id = create(ana, "Apagada");
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setPeladaId(id);
        payment.setPeladaName("Apagada");
        payment.setPayer(ana);
        payment.setPayerName("ana");
        payment.setAmount(new BigDecimal("19.90"));
        payment.setPaidOn(LocalDate.now());
        payment.setPlan(SubscriptionPlan.MONTHLY);
        payment.setPeriodStart(Instant.now());
        payment.setPeriodEnd(Instant.now().plus(Duration.ofDays(30)));
        payment.setRecordedBy(ana);
        payment.setCreatedAt(Instant.now());
        paymentRepository.save(payment);
        reload();

        mockMvc.perform(delete("/api/v1/peladas/" + id)
                        .header("Authorization", bearerToken(ana.getId(), ana.getEmail())))
                .andExpect(status().isNoContent());
        reload();

        assertThat(subscriptionRepository.findByPeladaId(id)).isEmpty();
        assertThat(eventRepository.findAll()).isEmpty();
        SubscriptionPayment kept = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(kept.getPeladaId()).isNull();
        assertThat(kept.getPeladaName()).isEqualTo("Apagada");
    }

    private long create(User creator, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/peladas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearerToken(creator.getId(), creator.getEmail()))
                        .content("{\"name\":\"" + name + "\",\"dayOfWeek\":\"FRIDAY\",\"timeOfDay\":\"20:00\",\"duration\":1.5}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    // read what the database holds, not the persistence context of the test transaction
    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }
}
