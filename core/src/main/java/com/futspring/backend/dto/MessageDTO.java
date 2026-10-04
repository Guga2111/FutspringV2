package com.futspring.backend.dto;

import com.futspring.backend.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    private Long id;
    private String content;
    private String sentAt;
    private SenderDTO sender;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SenderDTO {
        private Long id;
        private String username;
        private String image;
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
                        .build())
                .build();
    }
}
