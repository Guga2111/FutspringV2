package com.futspring.backend.controller;

import com.futspring.backend.dto.MessageDTO;
import com.futspring.backend.dto.SendMessageRequest;
import com.futspring.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/pelada/{peladaId}/send")
    public void sendMessage(
            @DestinationVariable Long peladaId,
            @Payload SendMessageRequest request,
            Principal principal
    ) {
        String senderEmail = principal.getName();
        MessageDTO messageDTO = chatService.saveAndBroadcast(peladaId, senderEmail, request.getContent());
        messagingTemplate.convertAndSend("/topic/pelada/" + peladaId, messageDTO);
    }
}
