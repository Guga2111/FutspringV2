package com.futspring.backend.domain.auth.dto;

import com.futspring.backend.domain.user.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record UserResponseDTO(
        Long id,
        String email,
        String username,
        String image,
        String backgroundImage,
        String position,
        // Operator of the platform: the web app shows the "Admin" panel (the API answers 404 to everyone else)
        @JsonProperty("isPlatformAdmin")
        boolean isPlatformAdmin) {

    public static UserResponseDTO from(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .image(user.getImage())
                .backgroundImage(user.getBackgroundImage())
                .position(user.getPosition())
                .isPlatformAdmin(user.isPlatformAdmin())
                .build();
    }
}
