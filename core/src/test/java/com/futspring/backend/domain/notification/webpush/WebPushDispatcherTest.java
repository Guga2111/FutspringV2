package com.futspring.backend.domain.notification.webpush;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.notification.Notification;
import com.futspring.backend.domain.notification.NotificationAward;
import com.futspring.backend.domain.notification.NotificationCategory;
import com.futspring.backend.domain.notification.NotificationContent;
import com.futspring.backend.domain.notification.NotificationRepository;
import com.futspring.backend.domain.notification.NotificationType;
import com.futspring.backend.domain.notification.preference.NotificationPreference;
import com.futspring.backend.domain.notification.preference.NotificationPreferenceRepository;
import com.futspring.backend.domain.notification.preference.NotificationPreferenceService;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebPushDispatcherTest {

    // 2026-10-31 17:00 in São Paulo; the session starts at 20:00 there (23:00Z)
    static final Instant NOW = Instant.parse("2026-10-31T20:00:00Z");

    @Mock NotificationRepository notificationRepository;
    @Mock PushSubscriptionRepository subscriptionRepository;
    @Mock NotificationPreferenceRepository preferenceRepository;

    FakeSender sender;
    SimpleMeterRegistry meterRegistry;
    JsonMapper jsonMapper = JsonMapper.builder().build();
    User leal;
    User ana;
    User bia;
    Pelada pelada;
    Daily daily;
    List<PushSubscription> subscriptions;

    // Records every send and answers the status set for its endpoint (201 by default)
    public static class FakeSender implements WebPushSender {
        final List<Sent> sent = new ArrayList<>();
        final Map<String, Object> answers = new HashMap<>();
        boolean enabled = true;

        record Sent(PushSubscription subscription, String payload, Options options) {
        }

        @Override
        public int send(PushSubscription subscription, byte[] payload, Options options) throws IOException {
            sent.add(new Sent(subscription, new String(payload, StandardCharsets.UTF_8), options));
            Object answer = answers.getOrDefault(subscription.getEndpoint(), 201);
            if (answer instanceof IOException e) {
                throw e;
            }
            return (Integer) answer;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }
    }

    @BeforeEach
    void setUp() {
        sender = new FakeSender();
        meterRegistry = new SimpleMeterRegistry();
        leal = User.builder().id(1L).email("leal@example.com").username("Leal").password("hash").build();
        ana = User.builder().id(2L).email("ana@example.com").username("ana").password("hash").build();
        bia = User.builder().id(3L).email("bia@example.com").username("bia").password("hash").build();
        pelada = Pelada.builder().id(10L).name("Pelada do Fut").build();
        daily = Daily.builder().id(100L).pelada(pelada).dailyDate(LocalDate.of(2026, 10, 31)).dailyTime("20:00").build();
        subscriptions = new ArrayList<>();
    }

    private WebPushDispatcher dispatcher(Instant now) {
        NotificationPreferenceService preferenceService =
                new NotificationPreferenceService(null, preferenceRepository, null, null);
        return new WebPushDispatcher(notificationRepository, subscriptionRepository, preferenceService, sender,
                jsonMapper, Clock.fixed(now, ZoneOffset.UTC), TransactionOperations.withoutTransaction(),
                TransactionOperations.withoutTransaction(), meterRegistry);
    }

    private WebPushDispatcher dispatcher() {
        return dispatcher(NOW);
    }

    private Notification notification(long id, NotificationType type, User recipient) {
        return Notification.builder().id(id).type(type).recipient(recipient).actor(leal).pelada(pelada)
                .daily(type.category() == NotificationCategory.MEMBERSHIP ? null : daily)
                .invitationId(type == NotificationType.PELADA_INVITATION ? 7L : null)
                .award(type == NotificationType.AWARD_WON ? NotificationAward.GARCOM : null)
                .createdAt(NOW).build();
    }

    private PushSubscription device(long id, User user, String name) {
        PushSubscription s = new PushSubscription(user, "https://fcm.googleapis.com/fcm/send/" + name,
                PushTestKeys.p256dh(), PushTestKeys.auth(), NOW);
        s.setId(id);
        subscriptions.add(s);
        return s;
    }

    private void stub(Notification... rows) {
        when(notificationRepository.findAllByIdInWithRefs(anyCollection())).thenReturn(List.of(rows));
        lenient().when(subscriptionRepository.findByUserIdIn(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return subscriptions.stream().filter(s -> ids.contains(s.getUser().getId())).toList();
        });
        lenient().when(preferenceRepository.findByIdUserIdIn(anyCollection())).thenReturn(List.of());
        lenient().when(notificationRepository.countUnreadByRecipients(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{ana.getId(), 3L}));
    }

    private JsonNode payload(int index) {
        return jsonMapper.readTree(sender.sent.get(index).payload());
    }

    private double count(String result) {
        return meterRegistry.counter(WebPushDispatcher.METRIC, "result", result).count();
    }

    // --- the send matrix: type × pushable × preference × default ---

    @Test
    void sendsEveryPushableTypeWithTheDefaultPreference() {
        device(1L, ana, "ana");
        for (NotificationType type : NotificationType.values()) {
            sender.sent.clear();
            reset(notificationRepository, preferenceRepository);
            stub(notification(1L, type, ana));

            dispatcher().send(List.of(1L));

            boolean expected = type.pushable() && type.category().defaultPush();
            assertThat(sender.sent).as("%s with the default preference", type).hasSize(expected ? 1 : 0);
        }
        assertThat(NotificationType.ADMIN_REVOKED.pushable()).isFalse();
        assertThat(NotificationType.TEAMS_SORTED.pushable()).isTrue();
        assertThat(NotificationType.TEAMS_SORTED.emailable()).isFalse();
    }

    @Test
    void aStoredPushPreferenceTurnsTheCategoryOff_andAnEmailOnlyRowKeepsTheDefault() {
        device(1L, ana, "ana");
        device(2L, bia, "bia");
        stub(notification(1L, NotificationType.TEAMS_SORTED, ana), notification(2L, NotificationType.TEAMS_SORTED, bia));
        when(preferenceRepository.findByIdUserIdIn(anyCollection())).thenReturn(List.of(
                new NotificationPreference(ana, NotificationCategory.SESSION, false, false),
                new NotificationPreference(bia, NotificationCategory.SESSION, true, null)));

        dispatcher().send(List.of(1L, 2L));

        assertThat(sender.sent).extracting(s -> s.subscription().getUser().getId()).containsExactly(bia.getId());
    }

    @Test
    void subscriptionNotices_followTheBillingPushPreference() {
        device(1L, ana, "ana");
        device(2L, bia, "bia");
        stub(notification(1L, NotificationType.SUBSCRIPTION_ENDING, ana),
                notification(2L, NotificationType.SUBSCRIPTION_PIX_CLAIMED, bia));
        when(preferenceRepository.findByIdUserIdIn(anyCollection())).thenReturn(List.of(
                new NotificationPreference(ana, NotificationCategory.BILLING, true, false)));

        dispatcher().send(List.of(1L, 2L));

        assertThat(sender.sent).extracting(s -> s.subscription().getUser().getId()).containsExactly(bia.getId());
    }

    @Test
    void everyDeviceOfTheRecipientGetsIt_andRecipientsWithoutDevicesNeedNoPreferenceQuery() {
        device(1L, ana, "phone");
        device(2L, ana, "laptop");
        stub(notification(1L, NotificationType.SESSION_CANCELED, ana), notification(2L, NotificationType.SESSION_CANCELED, bia));

        dispatcher().send(List.of(1L, 2L));

        assertThat(sender.sent).extracting(s -> s.subscription().getId()).containsExactly(1L, 2L);
        verify(preferenceRepository).findByIdUserIdIn(List.of(ana.getId()));
    }

    @Test
    void nobodySubscribed_sendsNothingAfterOneQuery() {
        stub(notification(1L, NotificationType.SESSION_CANCELED, ana));

        dispatcher().send(List.of(1L));

        assertThat(sender.sent).isEmpty();
        verifyNoInteractions(preferenceRepository);
        verify(notificationRepository, never()).countUnreadByRecipients(any());
    }

    // --- payload and headers ---

    @Test
    void thePayloadCarriesTitleBodyUrlTagAndTheUnreadCount() {
        device(1L, ana, "ana");
        stub(notification(7L, NotificationType.TEAMS_SORTED, ana));

        dispatcher().send(List.of(7L));

        JsonNode json = payload(0);
        assertThat(json.get("title").asString()).isEqualTo("Times definidos");
        assertThat(json.get("body").asString()).isEqualTo("Os times da sessão de sáb, 31/10 às 20:00 da Pelada do Fut foram definidos.");
        assertThat(json.get("url").asString()).isEqualTo("/daily/100?notificacao=7");
        assertThat(json.get("tag").asString()).isEqualTo("TEAMS_SORTED-100");
        assertThat(json.get("unreadCount").asLong()).isEqualTo(3L);
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("title", "body", "url", "tag", "unreadCount");
    }

    @Test
    void aMembershipNotificationIsTaggedByPelada_andLinksToItsPage() {
        device(1L, ana, "ana");
        stub(notification(8L, NotificationType.PELADA_INVITATION, ana));

        dispatcher().send(List.of(8L));

        assertThat(payload(0).get("url").asString()).isEqualTo("/invitations/7?notificacao=8");
        assertThat(payload(0).get("tag").asString()).isEqualTo("PELADA_INVITATION-10");
    }

    @Test
    void aLongBodyIsCutAt180Characters() {
        pelada.setName("P".repeat(300));
        device(1L, ana, "ana");
        stub(notification(1L, NotificationType.SESSION_CANCELED, ana));

        dispatcher().send(List.of(1L));

        String body = payload(0).get("body").asString();
        assertThat(body).hasSize(NotificationContent.PUSH_BODY_MAX).endsWith("…");
    }

    @Test
    void attendanceIsHighUrgency_theRestNormal_andTheTopicFitsTheHeader() {
        device(1L, ana, "ana");
        stub(notification(1L, NotificationType.DIARISTA_BUMPED, ana), notification(2L, NotificationType.SESSION_FINISHED, ana));

        dispatcher().send(List.of(1L, 2L));

        WebPushSender.Options bumped = sender.sent.get(0).options();
        WebPushSender.Options finished = sender.sent.get(1).options();
        assertThat(bumped.urgency()).isEqualTo(WebPushSender.Urgency.HIGH);
        assertThat(bumped.ttl()).isEqualTo(Duration.ofHours(24));
        assertThat(finished.urgency()).isEqualTo(WebPushSender.Urgency.NORMAL);
        assertThat(bumped.topic()).hasSize(32).matches("[A-Za-z0-9_-]+").isNotEqualTo(finished.topic());
        assertThat(WebPushDispatcher.topic("ATTENDANCE_CONFIRMED_BY_ADMIN-12345678")).hasSize(32)
                .isEqualTo(WebPushDispatcher.topic("ATTENDANCE_CONFIRMED_BY_ADMIN-12345678"));
    }

    @Test
    void aReminderLivesUntilTheSessionStarts_atLeastOneHour() {
        device(1L, ana, "ana");
        stub(notification(1L, NotificationType.SESSION_REMINDER, ana), notification(2L, NotificationType.DIARISTA_WINDOW_OPEN, ana));

        dispatcher().send(List.of(1L, 2L));
        assertThat(sender.sent).extracting(s -> s.options().ttl()).containsOnly(Duration.ofHours(3));

        sender.sent.clear();
        dispatcher(Instant.parse("2026-10-31T22:50:00Z")).send(List.of(1L));
        assertThat(sender.sent.getFirst().options().ttl()).isEqualTo(Duration.ofHours(1));

        sender.sent.clear();
        dispatcher(Instant.parse("2026-10-29T20:00:00Z")).send(List.of(1L));
        assertThat(sender.sent.getFirst().options().ttl()).isEqualTo(Duration.ofHours(24));
    }

    // --- responses ---

    @Test
    void goneSubscriptionsAreDeleted_successesMarked_failuresOnlyLogged() {
        PushSubscription ok = device(1L, ana, "ok");
        PushSubscription gone = device(2L, ana, "gone");
        PushSubscription notFound = device(3L, ana, "not-found");
        device(4L, ana, "server-error");
        device(5L, ana, "io-error");
        device(6L, ana, "bad-request");
        PushSubscription last = device(7L, ana, "last");
        sender.answers.put(gone.getEndpoint(), 410);
        sender.answers.put(notFound.getEndpoint(), 404);
        sender.answers.put("https://fcm.googleapis.com/fcm/send/server-error", 500);
        sender.answers.put("https://fcm.googleapis.com/fcm/send/io-error", new IOException("timeout"));
        sender.answers.put("https://fcm.googleapis.com/fcm/send/bad-request", 400);
        stub(notification(1L, NotificationType.SESSION_CANCELED, ana));

        dispatcher().send(List.of(1L));

        assertThat(sender.sent).hasSize(7);
        ArgumentCaptor<Collection<Long>> alive = ArgumentCaptor.captor();
        verify(subscriptionRepository).markSuccess(alive.capture(), eq(NOW));
        assertThat(alive.getValue()).containsExactly(ok.getId(), last.getId());
        ArgumentCaptor<Collection<Long>> deleted = ArgumentCaptor.captor();
        verify(subscriptionRepository).deleteByIdIn(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(gone.getId(), notFound.getId());
        assertThat(count("ok")).isEqualTo(2);
        assertThat(count("gone")).isEqualTo(2);
        assertThat(count("failed")).isEqualTo(3);
    }

    @Test
    void onlyFailures_writeNothing() {
        PushSubscription s = device(1L, ana, "down");
        sender.answers.put(s.getEndpoint(), 503);
        stub(notification(1L, NotificationType.SESSION_CANCELED, ana));

        dispatcher().send(List.of(1L));

        verify(subscriptionRepository, never()).markSuccess(any(), any());
        verify(subscriptionRepository, never()).deleteByIdIn(any());
    }

    @Test
    void aFailedBuildIsOnlyLogged() {
        when(notificationRepository.findAllByIdInWithRefs(anyCollection())).thenThrow(new IllegalStateException("db down"));

        dispatcher().send(List.of(1L));

        assertThat(sender.sent).isEmpty();
    }

    @Test
    void withoutAConfiguredSender_nothingIsRead() {
        sender.enabled = false;

        dispatcher().dispatch(List.of(1L));

        verifyNoInteractions(notificationRepository, subscriptionRepository, preferenceRepository);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
