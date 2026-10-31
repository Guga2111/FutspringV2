package com.futspring.backend.domain.auth.dto;


public record AuthResponseDTO(
        String token,
        UserResponseDTO user) {
}
