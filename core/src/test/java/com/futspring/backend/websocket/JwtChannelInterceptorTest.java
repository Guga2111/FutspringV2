package com.futspring.backend.websocket;

import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock JwtService jwtService;
    @Mock PeladaRepository peladaRepository;
    @Mock MessageChannel channel;

    JwtChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new JwtChannelInterceptor(jwtService, peladaRepository);
    }

    @Test
    void connect_withoutToken_isRejected() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connect_withInvalidToken_isRejected() {
        when(jwtService.isTokenValid("bad")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer bad", null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connect_withValidToken_setsUser() {
        when(jwtService.isTokenValid("good")).thenReturn(true);
        when(jwtService.extractEmail("good")).thenReturn("member@example.com");

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("member@example.com");
    }

    @Test
    void subscribe_withoutUser_isRejected() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/pelada/10", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(peladaRepository);
    }

    @Test
    void subscribe_nonMember_isRejected() {
        when(peladaRepository.existsByIdAndMembers_Email(10L, "out@example.com")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/pelada/10", "out@example.com"), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_member_isAccepted() {
        when(peladaRepository.existsByIdAndMembers_Email(10L, "member@example.com")).thenReturn(true);

        Message<?> message = frame(StompCommand.SUBSCRIBE, null, "/topic/pelada/10", "member@example.com");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void subscribe_ownErrorQueue_isAccepted() {
        Message<?> message = frame(StompCommand.SUBSCRIBE, null, "/user/queue/errors", "member@example.com");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        verifyNoInteractions(peladaRepository);
    }

    @Test
    void subscribe_otherDestination_isRejected() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/other", "member@example.com"), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void send_withoutUser_isRejected() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, null, "/app/pelada/10/send", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private static Message<byte[]> frame(StompCommand command, String authorization, String destination, String userEmail) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (userEmail != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(userEmail, null, List.of()));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
