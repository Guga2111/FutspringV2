package com.futspring.backend.domain.notification.webpush;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PushControllerTest extends BaseIntegrationTest {

    private static final String SUBSCRIPTIONS = "/api/v1/push/subscriptions";
    // POST /push/subscriptions is rate limited per IP: each request gets its own address unless a test wants one
    private static final AtomicInteger IPS = new AtomicInteger();

    @Autowired private UserRepository userRepository;
    @Autowired private PushSubscriptionRepository subscriptionRepository;
    @Autowired private EntityManager entityManager;

    private User ana;
    private User bia;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(User.builder().email("push-ana@test.com").username("push-ana").password("hash").build());
        bia = userRepository.save(User.builder().email("push-bia@test.com").username("push-bia").password("hash").build());
    }

    @Test
    void everyRouteNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/v1/push/vapid-public-key")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(SUBSCRIPTIONS).with(nextIp()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(endpoint("a"), PushTestKeys.p256dh(), PushTestKeys.auth())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(SUBSCRIPTIONS + "/remove").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("endpoint", endpoint("a")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutVapidKeysThePublicKeyIs204() throws Exception {
        mockMvc.perform(get("/api/v1/push/vapid-public-key").header("Authorization", auth(ana)))
                .andExpect(status().isNoContent());
    }

    @Test
    void subscribeCreatesThenUpdatesTheKeysOfTheSameEndpoint() throws Exception {
        String endpoint = endpoint("device-1");
        subscribe(ana, endpoint, PushTestKeys.p256dh(), "c2VjcmV0LWF1dGgtMTZiIQ").andExpect(status().isNoContent());
        String newKey = PushTestKeys.p256dh();
        String newAuth = PushTestKeys.auth();
        subscribe(ana, endpoint, newKey, newAuth).andExpect(status().isNoContent());

        List<PushSubscription> rows = subscriptionRepository.findByUserIdIn(List.of(ana.getId()));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getEndpoint()).isEqualTo(endpoint);
        assertThat(rows.getFirst().getP256dh()).isEqualTo(newKey);
        assertThat(rows.getFirst().getAuth()).isEqualTo(newAuth);
        assertThat(rows.getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    void anEndpointOfAnotherAccountMovesToTheCaller() throws Exception {
        String endpoint = endpoint("shared-phone");
        subscribe(ana, endpoint, PushTestKeys.p256dh(), PushTestKeys.auth()).andExpect(status().isNoContent());
        subscribe(bia, endpoint, PushTestKeys.p256dh(), PushTestKeys.auth()).andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findByUserIdIn(List.of(ana.getId()))).isEmpty();
        assertThat(subscriptionRepository.findByUserIdIn(List.of(bia.getId())))
                .extracting(PushSubscription::getEndpoint).containsExactly(endpoint);
    }

    @Test
    void theEleventhSubscriptionDropsTheLeastRecentlyAliveOne() throws Exception {
        for (int i = 0; i < PushSubscriptionService.MAX_PER_USER; i++) {
            subscribe(ana, endpoint("d" + i), PushTestKeys.p256dh(), PushTestKeys.auth()).andExpect(status().isNoContent());
        }
        // d0 is the oldest by creation but delivered recently; d1 never delivered and is the oldest sign of life
        List<PushSubscription> rows = subscriptionRepository.findByUserIdIn(List.of(ana.getId()));
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (PushSubscription row : rows) {
            int index = Integer.parseInt(row.getEndpoint().substring(row.getEndpoint().lastIndexOf('d') + 1));
            row.setCreatedAt(base.plusSeconds(index));
            row.setLastSuccessAt(index == 0 ? base.plusSeconds(3600) : null);
        }
        subscriptionRepository.flush();
        entityManager.clear();

        subscribe(ana, endpoint("d10"), PushTestKeys.p256dh(), PushTestKeys.auth()).andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findByUserIdIn(List.of(ana.getId())))
                .hasSize(PushSubscriptionService.MAX_PER_USER)
                .extracting(PushSubscription::getEndpoint)
                .contains(endpoint("d0"), endpoint("d10"))
                .doesNotContain(endpoint("d1"));
    }

    @Test
    void removeDeletesOnlyTheCallersSubscription() throws Exception {
        String endpoint = endpoint("ana-phone");
        subscribe(ana, endpoint, PushTestKeys.p256dh(), PushTestKeys.auth()).andExpect(status().isNoContent());

        remove(bia, endpoint).andExpect(status().isNoContent());
        assertThat(subscriptionRepository.findByEndpoint(endpoint)).isPresent();

        remove(bia, endpoint("unknown")).andExpect(status().isNoContent());

        remove(ana, endpoint).andExpect(status().isNoContent());
        assertThat(subscriptionRepository.findByEndpoint(endpoint)).isEmpty();
    }

    @Test
    void endpointsOutsideTheAllowlistAreRejected() throws Exception {
        for (String endpoint : List.of("http://fcm.googleapis.com/fcm/send/x", "https://fcm.googleapis.com:8443/x",
                "https://push.apple.com.evil.com/x", "https://127.0.0.1/x", "https://internal.local/x")) {
            subscribe(ana, endpoint, PushTestKeys.p256dh(), PushTestKeys.auth())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Endpoint de notificação inválido"));
        }
        assertThat(subscriptionRepository.findByUserIdIn(List.of(ana.getId()))).isEmpty();
    }

    @Test
    void keysOfTheWrongSizeAreRejected() throws Exception {
        subscribe(ana, endpoint("x"), "BAAA", PushTestKeys.auth())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chaves de notificação inválidas"));
        subscribe(ana, endpoint("x"), PushTestKeys.p256dh(), "c2hvcnQ")
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingOrOversizedFieldsAreAValidationError() throws Exception {
        mockMvc.perform(post(SUBSCRIPTIONS).with(nextIp()).header("Authorization", auth(ana))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("endpoint", endpoint("x")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.keys").exists());
        subscribe(ana, "https://fcm.googleapis.com/" + "a".repeat(1100), PushTestKeys.p256dh(), PushTestKeys.auth())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endpoint").exists());
        subscribe(ana, endpoint("x"), "", PushTestKeys.auth())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['keys.p256dh']").exists());
    }

    @Test
    void subscribingIsRateLimitedPerIp() throws Exception {
        RequestPostProcessor sameIp = r -> {
            r.setRemoteAddr("10.77.0.1");
            return r;
        };
        for (int i = 0; i < 10; i++) {
            subscribe(ana, endpoint("rl" + i), PushTestKeys.p256dh(), PushTestKeys.auth(), sameIp)
                    .andExpect(status().isNoContent());
        }
        subscribe(ana, endpoint("rl10"), PushTestKeys.p256dh(), PushTestKeys.auth(), sameIp)
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions subscribe(User user, String endpoint, String p256dh, String authKey) throws Exception {
        return subscribe(user, endpoint, p256dh, authKey, nextIp());
    }

    private ResultActions subscribe(User user, String endpoint, String p256dh, String authKey,
                                    RequestPostProcessor ip) throws Exception {
        return mockMvc.perform(post(SUBSCRIPTIONS).with(ip).header("Authorization", auth(user))
                .contentType(MediaType.APPLICATION_JSON).content(body(endpoint, p256dh, authKey)));
    }

    private ResultActions remove(User user, String endpoint) throws Exception {
        return mockMvc.perform(post(SUBSCRIPTIONS + "/remove").header("Authorization", auth(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("endpoint", endpoint))));
    }

    private String body(String endpoint, String p256dh, String authKey) {
        return json(Map.of("endpoint", endpoint, "keys", Map.of("p256dh", p256dh, "auth", authKey)));
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private static String endpoint(String id) {
        return "https://fcm.googleapis.com/fcm/send/" + id;
    }

    private static RequestPostProcessor nextIp() {
        int n = IPS.incrementAndGet();
        return r -> {
            r.setRemoteAddr("10.66." + (n / 250) + "." + (n % 250 + 1));
            return r;
        };
    }

    private String auth(User user) {
        return bearerToken(user.getId(), user.getEmail());
    }
}
