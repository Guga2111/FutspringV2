package com.futspring.backend.domain.chat;

import com.futspring.backend.domain.chat.dto.MessageDTO;
import com.futspring.backend.domain.chat.dto.SendMessageRequest;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/pelada/{peladaId}/send")
    public void sendMessage(@DestinationVariable Long peladaId, @Payload SendMessageRequest request, Principal principal) {
        // JwtChannelInterceptor already rejects unauthenticated SENDs; this guards against a misconfiguration
        if (principal == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Não autenticado");
        }
        MessageDTO messageDTO = chatService.saveAndBroadcast(peladaId, principal.getName(), request.getContent());
        messagingTemplate.convertAndSend("/topic/pelada/" + peladaId, messageDTO);
    }

    // Errors go back to the sender only (the client subscribes to /user/queue/errors)
    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, Object> handleException(Exception ex) {
        if (ex instanceof AppException appException) {
            return Map.of("status", appException.getStatus().value(), "message", appException.getMessage(),
                    "timestamp", Instant.now().toString());
        }
        log.error("Unhandled chat error", ex);
        return Map.of("status", 500, "message", "Não foi possível enviar a mensagem", "timestamp", Instant.now().toString());
    }
}
