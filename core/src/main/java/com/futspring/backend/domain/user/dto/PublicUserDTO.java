package com.futspring.backend.domain.user.dto;

import com.futspring.backend.domain.user.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

// Public view of another user: no email or other PII
@Builder
public record PublicUserDTO(
        Long id,
        String username,
        String image,
        String position,
        // A deleted account ("Ex-jogador"): shown without a link to the profile
        @JsonProperty("isDeleted")
        boolean isDeleted) {

    public static PublicUserDTO from(User user) {
        return PublicUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .image(user.getImage())
                .position(user.getPosition())
                .isDeleted(user.isDeleted())
                .build();
    }
}
