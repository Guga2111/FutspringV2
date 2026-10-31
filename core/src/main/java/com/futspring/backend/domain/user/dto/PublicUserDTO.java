package com.futspring.backend.domain.user.dto;

import com.futspring.backend.domain.user.User;
import lombok.Builder;

// Public view of another user: no email or other PII
@Builder
public record PublicUserDTO(
        Long id,
        String username,
        String image,
        String position) {

    public static PublicUserDTO from(User user) {
        return PublicUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .image(user.getImage())
                .position(user.getPosition())
                .build();
    }
}
