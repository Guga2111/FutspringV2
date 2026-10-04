package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.pelada.PeladaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates and authorizes STOMP frames:
 * CONNECT needs a valid Bearer token; SUBSCRIBE is only allowed to /topic/pelada/{id} for members of that
 * pelada and to the user's own error queue; SEND needs an authenticated session and an /app destination
 * (a SEND straight to /topic or /queue would reach the broker's subscribers without going through ChatService).
 */
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    static final String ERROR_QUEUE = "/user/queue/errors";
    private static final String APP_PREFIX = "/app/";
    private static final Pattern PELADA_TOPIC = Pattern.compile("^/topic/pelada/(\\d+)$");

    private final JwtService jwtService;
    private final PeladaRepository peladaRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            default -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Token ausente");
        }
        String token = authHeader.substring(7);
        if (!jwtService.isTokenValid(token)) {
            throw new MessageDeliveryException("Token inválido ou expirado");
        }
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                jwtService.extractEmail(token), null, Collections.emptyList()));
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal user = requireUser(accessor);
        String destination = accessor.getDestination();
        if (ERROR_QUEUE.equals(destination)) {
            return;
        }
        Matcher matcher = destination != null ? PELADA_TOPIC.matcher(destination) : null;
        if (matcher == null || !matcher.matches()) {
            throw new MessageDeliveryException("Destino não permitido");
        }
        Long peladaId = Long.valueOf(matcher.group(1));
        if (!peladaRepository.existsByIdAndMembers_Email(peladaId, user.getName())) {
            throw new MessageDeliveryException("Acesso negado: você não é membro desta pelada");
        }
    }

    private static void authorizeSend(StompHeaderAccessor accessor) {
        requireUser(accessor);
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(APP_PREFIX)) {
            throw new MessageDeliveryException("Destino não permitido");
        }
    }

    private static Principal requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new MessageDeliveryException("Não autenticado");
        }
        return user;
    }
}
