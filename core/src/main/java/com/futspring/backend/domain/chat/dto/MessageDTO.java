package com.futspring.backend.domain.chat.dto;

import com.futspring.backend.domain.chat.Message;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record MessageDTO(
        Long id,
        String content,
        String sentAt,
        SenderDTO sender) {

    @Builder
    public record SenderDTO(
            Long id,
            String username,
            String image,
            // a deleted account: the message stays, signed "Ex-jogador"
            @JsonProperty("isDeleted")
            boolean isDeleted) {
    }

    public static MessageDTO from(Message message) {
        return MessageDTO.builder()
                .id(message.getId())
                .content(message.getContent())
                .sentAt(message.getSentAt().toString())
                .sender(SenderDTO.builder()
                        .id(message.getSender().getId())
                        .username(message.getSender().getUsername())
                        .image(message.getSender().getImage())
                        .isDeleted(message.getSender().isDeleted())
                        .build())
                .build();
    }
}
