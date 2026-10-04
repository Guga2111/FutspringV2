package com.futspring.backend.dto;

import com.futspring.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDTO {

    private Long id;
    private String email;
    private String username;
    private String image;
    private String backgroundImage;
    private int stars;
    private String position;

    public static UserResponseDTO from(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .image(user.getImage())
                .backgroundImage(user.getBackgroundImage())
                .stars(user.getStars())
                .position(user.getPosition())
                .build();
    }
}
