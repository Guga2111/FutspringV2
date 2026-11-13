package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.pelada.MembersRemovedEvent;
import com.futspring.backend.domain.user.TokensRevokedEvent;
import com.futspring.backend.domain.user.User;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class StompSessions {

    private static final String PELADA_TOPIC = "/topic/pelada/";

    // Lazy: both live in the broker configuration, which reads WebSocketConfig, which needs this bean
    private final ObjectProvider<SimpUserRegistry> userRegistry;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplate;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** Keeps every open WebSocketSession by id (the STOMP simpSessionId). */
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                sessions.remove(session.getId());
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
            WebSocketSession webSocketSession = sessions.get(session.getId());
            if (webSocketSession == null) continue;
            try {
                webSocketSession.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ex) {
                log.warn("Could not close STOMP session {}", session.getId(), ex);
            }
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
