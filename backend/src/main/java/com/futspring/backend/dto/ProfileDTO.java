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
public class ProfileDTO {

    private Long id;
    private String username;
    private String email;
    private String image;
    private String backgroundImage;
    private int stars;
    private String position;

    public static ProfileDTO from(User user) {
        return ProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .image(user.getImage())
                .backgroundImage(user.getBackgroundImage())
                .stars(user.getStars())
                .position(user.getPosition())
                .build();
    }
}
