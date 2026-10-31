package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.mail.EmailSender;
import com.futspring.backend.domain.mail.MailConfig;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.config.JwtConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEmailDispatcherTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationPreferenceRepository preferenceRepository;
    @Mock EmailSender emailSender;
    @Mock EmailPacer pacer;

    NotificationEmailDispatcher dispatcher;
    SimpleMeterRegistry meterRegistry;
    UnsubscribeTokens tokens;
    List<Runnable> queued;
    User leal;
    User ana;
    User bia;
    Pelada pelada;
    Daily daily;

    @BeforeEach
    void setUp() {
        JwtConfig jwtConfig = new JwtConfig();
        ReflectionTestUtils.setField(jwtConfig, "secret", "test-secret-key-that-is-at-least-32-characters-long-for-hmac");
        tokens = new UnsubscribeTokens(jwtConfig);
        MailConfig mailConfig = new MailConfig();
        ReflectionTestUtils.setField(mailConfig, "frontendUrl", "https://app.example.com");
        ReflectionTestUtils.setField(mailConfig, "apiUrl", "https://app.example.com/api");
        NotificationPreferenceService preferenceService =
                new NotificationPreferenceService(null, preferenceRepository, null, tokens);
        queued = new ArrayList<>();
        meterRegistry = new SimpleMeterRegistry();
        Executor executor = queued::add;
        dispatcher = new NotificationEmailDispatcher(notificationRepository, preferenceService, tokens, emailSender,
                mailConfig, TransactionOperations.withoutTransaction(), pacer, executor, meterRegistry);

        leal = User.builder().id(1L).email("leal@example.com").username("Leal").password("hash").build();
        ana = User.builder().id(2L).email("ana@example.com").username("ana").password("hash").build();
        bia = User.builder().id(3L).email("bia@example.com").username("bia").password("hash").build();
        pelada = Pelada.builder().id(10L).name("Pelada do Fut").build();
        daily = Daily.builder().id(100L).pelada(pelada).dailyDate(LocalDate.of(2026, 10, 31)).dailyTime("20:00").build();
    }

    private Notification notification(long id, NotificationType type, User recipient) {
        return Notification.builder().id(id).type(type).recipient(recipient).actor(leal).pelada(pelada)
                .daily(type.category() == NotificationCategory.MEMBERSHIP ? null : daily)
                .award(type == NotificationType.AWARD_WON ? NotificationAward.GARCOM : null)
                .createdAt(Instant.now()).build();
    }

    private void stubRows(Notification... rows) {
        when(notificationRepository.findAllByIdInWithRefs(anyCollection())).thenReturn(List.of(rows));
    }

    private void stubPreferences(NotificationPreference... rows) {
        when(preferenceRepository.findByIdUserIdIn(anyCollection())).thenReturn(List.of(rows));
    }

    private void runQueued() {
        queued.forEach(Runnable::run);
    }

    private List<String> sentTo() {
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeast(0)).send(to.capture(), anyString(), anyString(), anyList(), anyMap());
        return to.getAllValues();
    }

    // --- the send matrix: type × preference × default ---

    @Test
    void sendsWhenTheTypeIsEmailableAndTheCategoryIsOn() {
        when(emailSender.enabled()).thenReturn(true);
        Map<NotificationType, Boolean> expected = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            expected.put(type, type.emailable() && type.category().defaultEmail());
        }

        for (NotificationType type : NotificationType.values()) {
            reset(emailSender, notificationRepository, preferenceRepository);
            when(emailSender.enabled()).thenReturn(true);
            queued.clear();
            when(notificationRepository.findAllByIdInWithRefs(anyCollection()))
                    .thenReturn(List.of(notification(1L, type, ana)));
            lenient().when(preferenceRepository.findByIdUserIdIn(anyCollection())).thenReturn(List.of());

            dispatcher.dispatch(List.of(1L));
            runQueued();

            assertThat(sentTo()).as("%s with the default preference", type)
                    .isEqualTo(expected.get(type) ? List.of("ana@example.com") : List.of());
        }
    }

    @Test
    void aStoredPreference_overridesTheDefaultBothWays() {
        when(emailSender.enabled()).thenReturn(true);
        stubRows(notification(1L, NotificationType.DIARISTA_BUMPED, ana),        // ATTENDANCE, default on
                notification(2L, NotificationType.DIARISTA_BUMPED, bia),
                notification(3L, NotificationType.SESSION_CREATED, ana),          // SESSION, default off
                notification(4L, NotificationType.SESSION_CREATED, bia));
        stubPreferences(new NotificationPreference(ana, NotificationCategory.ATTENDANCE, false),
                new NotificationPreference(bia, NotificationCategory.SESSION, true));

        dispatcher.dispatch(List.of(1L, 2L, 3L, 4L));
        runQueued();

        // ana turned Presença off; bia turned Sessões on
        assertThat(sentTo()).containsExactly("bia@example.com", "bia@example.com");
        verify(preferenceRepository, times(1)).findByIdUserIdIn(anyCollection());
    }

    @Test
    void invitationEmailRespectsMembershipPreferenceAndJoinStaysInApp() {
        when(emailSender.enabled()).thenReturn(true);
        Notification invitation = notification(1L, NotificationType.PELADA_INVITATION, ana);
        invitation.setInvitationId(42L);
        stubRows(invitation, notification(2L, NotificationType.MEMBER_JOINED, bia));
        stubPreferences(new NotificationPreference(ana, NotificationCategory.MEMBERSHIP, false));
        dispatcher.dispatch(List.of(1L, 2L));
        runQueued();
        assertThat(sentTo()).isEmpty();
    }

    @Test
    void nonEmailableTypes_neverSend_evenWithTheCategoryOn() {
        when(emailSender.enabled()).thenReturn(true);
        stubRows(notification(1L, NotificationType.ATTENDANCE_CONFIRMED_BY_ADMIN, ana),
                notification(2L, NotificationType.TEAMS_SORTED, ana),
                notification(3L, NotificationType.ADMIN_REVOKED, ana));

        dispatcher.dispatch(List.of(1L, 2L, 3L));
        runQueued();

        assertThat(sentTo()).isEmpty();
        verifyNoInteractions(preferenceRepository);
    }

    // --- the e-mail itself ---

    @Test
    void theEmailCarriesTheTextLinksLogoAndOneClickHeaders() throws InterruptedException {
        when(emailSender.enabled()).thenReturn(true);
        stubRows(notification(1L, NotificationType.DIARISTA_BUMPED, ana));
        stubPreferences();

        dispatcher.dispatch(List.of(1L));
        runQueued();

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(emailSender).send(eq("ana@example.com"), eq("Você perdeu a vaga na Pelada do Fut"), html.capture(),
                eq(List.of(NotificationEmail.LOGO)), headers.capture());
        verify(pacer).acquire();

        String token = tokens.create(ana.getId(), NotificationCategory.ATTENDANCE);
        assertThat(html.getValue())
                .contains("href=\"https://app.example.com/daily/100\"")
                .contains("href=\"https://app.example.com/settings/notifications\"")
                .contains("href=\"https://app.example.com/notifications/unsubscribe?token=" + token + "\"")
                .contains("src=\"cid:" + NotificationEmail.LOGO.contentId() + "\"");
        assertThat(headers.getValue())
                .containsEntry("List-Unsubscribe",
                        "<https://app.example.com/api/api/v1/notifications/unsubscribe?token=" + token + ">")
                .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
    }

    // --- failures and the executor ---

    @Test
    void oneFailedSend_doesNotStopTheOthers() {
        when(emailSender.enabled()).thenReturn(true);
        stubRows(notification(1L, NotificationType.SESSION_CANCELED, ana),
                notification(2L, NotificationType.SESSION_CANCELED, bia));
        stubPreferences();
        doThrow(new RestClientException("Resend down")).when(emailSender)
                .send(eq("ana@example.com"), anyString(), anyString(), anyList(), anyMap());

        dispatcher.dispatch(List.of(1L, 2L));
        runQueued();

        assertThat(sentTo()).containsExactly("ana@example.com", "bia@example.com");
        assertThat(meterRegistry.get("futspring.email.sent").tag("result", "ok").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("futspring.email.sent").tag("result", "failed").counter().count()).isEqualTo(1);
    }

    @Test
    void theQueueSizeGauge_readsTheExecutorQueue() throws InterruptedException {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(10);
        pool.initialize();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new NotificationEmailDispatcher(notificationRepository,
                new NotificationPreferenceService(null, preferenceRepository, null, tokens), tokens, emailSender,
                new MailConfig(), TransactionOperations.withoutTransaction(), pacer, pool, registry);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            pool.execute(() -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            running.await();
            pool.execute(() -> { });
            pool.execute(() -> { });

            assertThat(registry.get("futspring.email.queue.size").gauge().value()).isEqualTo(2);
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }

    @Test
    void everySendWaitsForThePacer() throws InterruptedException {
        when(emailSender.enabled()).thenReturn(true);
        stubRows(notification(1L, NotificationType.SESSION_CANCELED, ana),
                notification(2L, NotificationType.SESSION_CANCELED, bia),
                notification(3L, NotificationType.SESSION_CANCELED, leal));
        stubPreferences();

        dispatcher.dispatch(List.of(1L, 2L, 3L));
        runQueued();

        verify(pacer, times(3)).acquire();
    }

    @Test
    void dispatch_onlyQueues_theRequestThreadNeverQueriesNorSends() {
        when(emailSender.enabled()).thenReturn(true);

        dispatcher.dispatch(List.of(1L));

        assertThat(queued).hasSize(1);
        verifyNoInteractions(notificationRepository, preferenceRepository);
        verify(emailSender, never()).send(anyString(), anyString(), anyString(), anyList(), anyMap());
    }

    @Test
    void withoutARealSender_nothingIsQueued() {
        when(emailSender.enabled()).thenReturn(false);

        dispatcher.dispatch(List.of(1L));

        assertThat(queued).isEmpty();
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void aFullQueue_isOnlyLogged() {
        when(emailSender.enabled()).thenReturn(true);
        NotificationEmailDispatcher full = new NotificationEmailDispatcher(notificationRepository,
                new NotificationPreferenceService(null, preferenceRepository, null, tokens), tokens, emailSender,
                new MailConfig(), TransactionOperations.withoutTransaction(), pacer,
                task -> { throw new TaskRejectedException("full"); }, meterRegistry);

        full.dispatch(List.of(1L));

        verifyNoInteractions(notificationRepository);
        assertThat(meterRegistry.get("futspring.email.dropped").counter().count()).isEqualTo(1);
    }

    @Test
    void aFailureWhileBuilding_isOnlyLogged() {
        when(emailSender.enabled()).thenReturn(true);
        when(notificationRepository.findAllByIdInWithRefs(anyCollection())).thenThrow(new IllegalStateException("db"));

        dispatcher.dispatch(List.of(1L));
        runQueued();

        verify(emailSender, never()).send(anyString(), anyString(), anyString(), anyList(), anyMap());
    }
}
