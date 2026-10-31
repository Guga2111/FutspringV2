package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.notification.dto.NotificationPushDTO;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPushServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Mock NotificationRepository notificationRepository;
    @Mock TeamRepository teamRepository;
    @Mock SimpMessagingTemplate messagingTemplate;

    NotificationPushService service;
    SimpleMeterRegistry meterRegistry;
    User admin;
    User member;
    User other;
    Pelada pelada;
    Daily daily;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new NotificationPushService(notificationRepository, new NotificationDTOAssembler(teamRepository),
                messagingTemplate, meterRegistry);
        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").build();
        member = User.builder().id(2L).email("member@example.com").username("member").password("hash").build();
        other = User.builder().id(3L).email("other@example.com").username("other").password("hash").build();
        pelada = Pelada.builder().id(10L).name("Pelada do Fut").creator(admin).build();
        daily = Daily.builder().id(100L).pelada(pelada).dailyDate(LocalDate.of(2026, 10, 31)).dailyTime("20:00").build();
    }

    private Notification notification(long id, NotificationType type, User recipient) {
        return Notification.builder().id(id).type(type).recipient(recipient).actor(admin).pelada(pelada).daily(daily)
                .createdAt(NOW).build();
    }

    private List<NotificationPushDTO> sentTo(String email, int times) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(times)).convertAndSendToUser(eq(email), eq("/queue/notifications"), payload.capture());
        return payload.getAllValues().stream().map(NotificationPushDTO.class::cast).toList();
    }

    @Test
    void push_sendsEachNotificationToItsRecipientWithTheirUnreadCount() {
        when(notificationRepository.findAllByIdInWithRefs(List.of(1L, 2L))).thenReturn(List.of(
                notification(1L, NotificationType.ATTENDANCE_REMOVED_BY_ADMIN, member),
                notification(2L, NotificationType.ATTENDANCE_REMOVED_BY_ADMIN, other)));
        when(notificationRepository.countUnreadByRecipients(List.of(2L, 3L)))
                .thenReturn(List.of(new Object[]{2L, 4L}, new Object[]{3L, 1L}));

        service.push(List.of(1L, 2L));

        assertThat(sentTo("member@example.com", 1)).singleElement().satisfies(p -> {
            assertThat(p.notification().id()).isEqualTo(1L);
            assertThat(p.notification().actor().username()).isEqualTo("admin");
            assertThat(p.notification().daily().id()).isEqualTo(100L);
            assertThat(p.unreadCount()).isEqualTo(4L);
        });
        assertThat(sentTo("other@example.com", 1)).singleElement()
                .satisfies(p -> assertThat(p.unreadCount()).isEqualTo(1L));
        verifyNoInteractions(teamRepository);
    }

    @Test
    void push_teamsSorted_carriesEachRecipientsTeam() {
        when(notificationRepository.findAllByIdInWithRefs(List.of(1L, 2L))).thenReturn(List.of(
                notification(1L, NotificationType.TEAMS_SORTED, member),
                notification(2L, NotificationType.TEAMS_SORTED, other)));
        when(notificationRepository.countUnreadByRecipients(anyList()))
                .thenReturn(List.of(new Object[]{2L, 1L}, new Object[]{3L, 1L}));
        when(teamRepository.findTeamsOfUsersInDailies(anyCollection(), anyCollection())).thenReturn(List.of(
                new Object[]{2L, 100L, "Time Azul", "#0000ff"},
                new Object[]{3L, 100L, "Time Verde", "#00ff00"}));

        service.push(List.of(1L, 2L));

        assertThat(sentTo("member@example.com", 1)).singleElement()
                .satisfies(p -> assertThat(p.notification().team().name()).isEqualTo("Time Azul"));
        assertThat(sentTo("other@example.com", 1)).singleElement()
                .satisfies(p -> assertThat(p.notification().team().name()).isEqualTo("Time Verde"));
    }

    @Test
    void push_oneFailedSend_doesNotStopTheOthers() {
        when(notificationRepository.findAllByIdInWithRefs(List.of(1L, 2L))).thenReturn(List.of(
                notification(1L, NotificationType.SESSION_CANCELED, member),
                notification(2L, NotificationType.SESSION_CANCELED, other)));
        when(notificationRepository.countUnreadByRecipients(anyList())).thenReturn(List.of());
        doThrow(new MessagingException("broker down")).when(messagingTemplate)
                .convertAndSendToUser(eq("member@example.com"), anyString(), any(Object.class));

        service.push(List.of(1L, 2L));

        assertThat(sentTo("other@example.com", 1)).singleElement()
                .satisfies(p -> assertThat(p.unreadCount()).isZero());
        assertThat(meterRegistry.get("futspring.notifications.push").tag("result", "ok").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("futspring.notifications.push").tag("result", "failed").counter().count())
                .isEqualTo(1);
    }

    @Test
    void push_rowsGone_sendsNothing() {
        when(notificationRepository.findAllByIdInWithRefs(List.of(1L))).thenReturn(List.of());

        service.push(List.of(1L));

        verifyNoInteractions(messagingTemplate);
        verify(notificationRepository, never()).countUnreadByRecipients(anyList());
    }

    @Test
    void push_repositoryFailure_isOnlyLogged() {
        when(notificationRepository.findAllByIdInWithRefs(List.of(1L))).thenThrow(new IllegalStateException("db down"));

        service.push(List.of(1L));

        verifyNoInteractions(messagingTemplate);
    }
}
