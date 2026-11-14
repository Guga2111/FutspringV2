package com.futspring.backend.domain.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StompSessionsTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private final Clock clock = mock(Clock.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ScheduledFuture<?> task = mock(ScheduledFuture.class);
    private final ObjectProvider<TaskScheduler> schedulers = mock(ObjectProvider.class);
    private final ObjectProvider<SimpUserRegistry> registry = mock(ObjectProvider.class);
    private final ObjectProvider<SimpMessagingTemplate> messaging = mock(ObjectProvider.class);
    private StompSessions sessions;
    private WebSocketHandler handler;
    private WebSocketSession transport;

    @BeforeEach
    void setUp() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(schedulers.getObject()).thenReturn(scheduler);
        doReturn(task).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        sessions = new StompSessions(registry, messaging, schedulers, clock);
        handler = sessions.decorate(mock(WebSocketHandler.class));
        transport = transport("one");
        handler.afterConnectionEstablished(transport);
    }

    @ParameterizedTest
    @EnumSource(value = SimpMessageType.class, names = {"MESSAGE", "SUBSCRIBE", "CONNECT_ACK"})
    void expiredTrafficIsDeniedEvenWhenTheSchedulerHasNotRun(SimpMessageType type) throws Exception {
        sessions.authenticate("one", NOW.plusSeconds(1));
        Message<?> message = frame("one", type);
        assertThat(sessions.preSend(message, null)).isSameAs(message);
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        assertThat(sessions.beforeHandle(message, null, null)).isNull();
        assertThat(sessions.preSend(message, null)).isNull();
        verify(transport, atLeastOnce()).close(CloseStatus.POLICY_VIOLATION);
        verify(task).cancel(false);
    }

    @Test
    void queuedMessagesAreCheckedAtExecution_andOtherRecipientsStillReceive() throws Exception {
        WebSocketSession other = transport("two");
        handler.afterConnectionEstablished(other);
        sessions.authenticate("one", NOW.plusSeconds(1));
        sessions.authenticate("two", NOW.plusSeconds(60));
        List<Runnable> queued = new ArrayList<>();
        List<Message<?>> delivered = new ArrayList<>();
        var channel = new ExecutorSubscribableChannel(queued::add);
        channel.addInterceptor(sessions);
        channel.subscribe(delivered::add);
        channel.send(frame("one", SimpMessageType.MESSAGE));
        Message<?> valid = frame("two", SimpMessageType.MESSAGE);
        channel.send(valid);
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        queued.forEach(Runnable::run);
        assertThat(delivered).containsExactly(valid);
        verify(other, never()).close(any());
    }

    @Test
    void unknownAndUnauthenticatedSessionsCannotDeliverApplicationMessages() {
        assertThat(sessions.preSend(frame("unknown", SimpMessageType.MESSAGE), null)).isNull();
        assertThat(sessions.preSend(frame(null, SimpMessageType.MESSAGE), null)).isNull();
        assertThat(sessions.preSend(frame("one", SimpMessageType.SUBSCRIBE), null)).isNull();
        assertThat(sessions.preSend(frame("one", SimpMessageType.DISCONNECT), null)).isNotNull();
    }

    @Test
    void expirationClosesIdleTransport_andDoesNotCloseAnotherConnection() throws Exception {
        List<Runnable> callbacks = new ArrayList<>();
        doAnswer(invocation -> { callbacks.add(invocation.getArgument(0)); return task; })
                .when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        WebSocketSession other = transport("two");
        handler.afterConnectionEstablished(other);
        sessions.authenticate("one", NOW.plusSeconds(1));
        sessions.authenticate("two", NOW.plusSeconds(60));
        callbacks.getFirst().run();
        verify(transport).close(CloseStatus.POLICY_VIOLATION);
        verify(other, never()).close(any());
    }

    @Test
    void closureFailureStillBlocksTraffic() throws Exception {
        sessions.authenticate("one", NOW.plusSeconds(1));
        doThrow(new IOException("test close failure")).when(transport).close(any());
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        assertThat(sessions.preSend(frame("one", SimpMessageType.MESSAGE), null)).isNull();
        assertThat(sessions.beforeHandle(frame("one", SimpMessageType.MESSAGE), null, null)).isNull();
    }

    @Test
    void rejectedSchedulingFailsClosed() throws Exception {
        doThrow(new RejectedExecutionException()).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        assertThatThrownBy(() -> sessions.authenticate("one", NOW.plusSeconds(1)))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(sessions.preSend(frame("one", SimpMessageType.MESSAGE), null)).isNull();
        verify(transport, atLeastOnce()).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void expiredMissingOrDuplicateAuthenticationIsRejected() {
        assertThatThrownBy(() -> sessions.authenticate("unknown", NOW.plusSeconds(1)))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> sessions.authenticate("one", NOW))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> sessions.authenticate("one", NOW.plusSeconds(1)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void secondConnectCannotExtendTheDeadline() {
        sessions.authenticate("one", NOW.plusSeconds(1));
        assertThatThrownBy(() -> sessions.authenticate("one", NOW.plusSeconds(60)))
                .isInstanceOf(MessageDeliveryException.class);
        verify(scheduler).schedule(any(Runnable.class), eq(NOW.plusSeconds(1)));
    }

    @Test
    void disconnectCancelsTask_andLateCallbackCannotCloseReplacement() throws Exception {
        List<Runnable> callbacks = new ArrayList<>();
        doAnswer(invocation -> { callbacks.add(invocation.getArgument(0)); return task; })
                .when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        sessions.authenticate("one", NOW.plusSeconds(1));
        handler.afterConnectionClosed(transport, CloseStatus.NORMAL);
        verify(task).cancel(false);
        WebSocketSession replacement = transport("one");
        handler.afterConnectionEstablished(replacement);
        sessions.authenticate("one", NOW.plusSeconds(60));
        callbacks.getFirst().run();
        assertThat(sessions.preSend(frame("one", SimpMessageType.MESSAGE), null)).isNotNull();
        verify(replacement, never()).close(any());
    }

    @Test
    void disconnectDuringSchedulingCancelsTheReturnedTask() {
        doAnswer(invocation -> {
            handler.afterConnectionClosed(transport, CloseStatus.NORMAL);
            return task;
        }).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        assertThatThrownBy(() -> sessions.authenticate("one", NOW.plusSeconds(1)))
                .isInstanceOf(MessageDeliveryException.class);
        verify(task).cancel(false);
    }

    @Test
    void shutdownCancelsTasks_andRefusesNewConnections() throws Exception {
        sessions.authenticate("one", NOW.plusSeconds(1));
        sessions.shutdown();
        verify(task).cancel(false);
        verify(transport).close(CloseStatus.POLICY_VIOLATION);
        assertThatThrownBy(() -> handler.afterConnectionEstablished(transport("new")))
                .isInstanceOf(MessageDeliveryException.class);
        assertThat(sessions.preSend(frame("one", SimpMessageType.MESSAGE), null)).isNull();
    }

    private static WebSocketSession transport(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }

    private static Message<?> frame(String id, SimpMessageType type) {
        var headers = SimpMessageHeaderAccessor.create(type);
        headers.setSessionId(id);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
