package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.pelada.MembersRemovedEvent;
import com.futspring.backend.domain.user.TokensRevokedEvent;
import com.futspring.backend.domain.user.User;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Takes back what STOMP authorized once, after the change commits (a rollback changes nothing):
 * a member removed from a pelada loses their subscriptions to /topic/pelada/{id} (the connection stays, so the bell
 * keeps working and a client still on the page doesn't loop on a refused SUBSCRIBE), and a tokenVersion change closes
 * every connection of the user (the client reconnects and its old token is refused at CONNECT).
 * The user's sessions and subscriptions come from Spring's SimpUserRegistry (keyed by the principal, the e-mail);
 * the WebSocketSessions, needed to close them, from the decorator below. In memory: one API instance, like the broker.
 */
@Slf4j
@Component
public class StompSessions implements ExecutorChannelInterceptor {

    private static final String PELADA_TOPIC = "/topic/pelada/";

    // Lazy: both live in the broker configuration, which reads WebSocketConfig, which needs this bean
    private final ObjectProvider<SimpUserRegistry> userRegistry;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplate;
    private final Map<String, Connection> sessions = new ConcurrentHashMap<>();
    private final ObjectProvider<TaskScheduler> scheduler;
    private final Clock clock;
    private volatile boolean stopping;

    public StompSessions(ObjectProvider<SimpUserRegistry> userRegistry,
                         ObjectProvider<SimpMessagingTemplate> messagingTemplate,
                         @Qualifier("taskScheduler") ObjectProvider<TaskScheduler> scheduler, Clock clock) {
        this.userRegistry = userRegistry;
        this.messagingTemplate = messagingTemplate;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    private static final class Connection {
        final WebSocketSession transport;
        Instant expiresAt;
        ScheduledFuture<?> task;
        boolean blocked;

        Connection(WebSocketSession transport) { this.transport = transport; }

        synchronized void block() {
            blocked = true;
            if (task != null) {
                task.cancel(false);
                task = null;
            }
        }
    }

    /** Called only with a deadline from AccessTokenVerifier, never from native client headers. */
    public void authenticate(String sessionId, Instant expiresAt) {
        Connection connection = sessionId == null ? null : sessions.get(sessionId);
        if (connection == null) throw invalid();
        try {
            synchronized (connection) {
                if (stopping || connection.blocked || connection.expiresAt != null
                        || expiresAt == null || !clock.instant().isBefore(expiresAt)) throw invalid();
                connection.expiresAt = expiresAt;
                connection.task = scheduler.getObject().schedule(() -> close(connection), expiresAt);
                if (connection.task == null) throw invalid();
                // Also handles an inline callback or a disconnect during registration.
                if (connection.blocked) {
                    connection.task.cancel(false);
                    connection.task = null;
                    throw invalid();
                }
            }
        } catch (RuntimeException ex) {
            close(connection);
            throw new MessageDeliveryException(null, "Não foi possível autenticar a conexão", ex);
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) { return authorize(message); }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        // Executor channels may queue a frame while its credentials are still valid.
        return authorize(message);
    }

    private Message<?> authorize(Message<?> message) {
        SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
        if (type != SimpMessageType.MESSAGE && type != SimpMessageType.SUBSCRIBE
                && type != SimpMessageType.CONNECT_ACK) return message;
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        Connection connection = sessionId == null ? null : sessions.get(sessionId);
        if (connection == null) return null;
        synchronized (connection) {
            if (!connection.blocked && connection.expiresAt != null
                    && clock.instant().isBefore(connection.expiresAt)) return message;
        }
        close(connection);
        return null;
    }

    private void close(Connection connection) {
        connection.block(); // Deny traffic even when physical closure fails.
        try {
            connection.transport.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ex) {
            log.warn("Could not close STOMP session {}", connection.transport.getId(), ex);
        }
    }

    private void forget(String id) {
        Connection connection = sessions.remove(id);
        if (connection != null) connection.block();
    }

    @PreDestroy
    void shutdown() {
        stopping = true;
        sessions.values().forEach(this::close);
        sessions.clear();
    }

    private static MessageDeliveryException invalid() {
        return new MessageDeliveryException("Sessão inválida ou expirada");
    }

    /** Keeps every open WebSocketSession by id (the STOMP simpSessionId). */
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                Connection connection = new Connection(session);
                sessions.put(session.getId(), connection);
                try {
                    if (stopping) throw invalid();
                    super.afterConnectionEstablished(session);
                } catch (Exception ex) {
                    forget(session.getId());
                    close(connection);
                    throw ex;
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                forget(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    @TransactionalEventListener
    public void onMembersRemoved(MembersRemovedEvent event) {
        String topic = PELADA_TOPIC + event.pelada().getId();
        for (User user : event.users()) {
            for (SimpSession session : sessionsOf(user.getEmail())) {
                for (SimpSubscription subscription : List.copyOf(session.getSubscriptions())) {
                    if (topic.equals(subscription.getDestination())) {
                        unsubscribe(session.getId(), subscription.getId());
                    }
                }
            }
        }
    }

    @TransactionalEventListener
    public void onTokensRevoked(TokensRevokedEvent event) {
        for (SimpSession session : sessionsOf(event.email())) {
            Connection connection = sessions.get(session.getId());
            if (connection != null) close(connection);
        }
    }

    // The SimpleBroker drops the subscription; DefaultSimpUserRegistry keeps a stale entry (it only follows client
    // frames), harmless: unsubscribing an unknown id again is a no-op
    private void unsubscribe(String sessionId, String subscriptionId) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        headers.setSessionId(sessionId);
        headers.setSubscriptionId(subscriptionId);
        try {
            messagingTemplate.getObject().getMessageChannel()
                    .send(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
        } catch (Exception ex) {
            log.warn("Could not unsubscribe STOMP session {}", sessionId, ex);
        }
    }

    private List<SimpSession> sessionsOf(String email) {
        SimpUser user = email == null ? null : userRegistry.getObject().getUser(email);
        return user == null ? List.of() : List.copyOf(user.getSessions());
    }
}
