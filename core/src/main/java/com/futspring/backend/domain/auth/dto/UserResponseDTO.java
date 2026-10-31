package com.futspring.backend.domain.auth.dto;

import com.futspring.backend.domain.user.User;
import lombok.Builder;

@Builder
public record UserResponseDTO(
        Long id,
        String email,
        String username,
        String image,
        String backgroundImage,
        String position) {

    public static UserResponseDTO from(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .image(user.getImage())
                .backgroundImage(user.getBackgroundImage())
                .position(user.getPosition())
                .build();
    }
}
