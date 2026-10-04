package com.futspring.backend.dto;

import com.futspring.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Public view of another user: no email or other PII
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicUserDTO {

    private Long id;
    private String username;
    private String image;
    private int stars;
    private String position;

    public static PublicUserDTO from(User user) {
        return PublicUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .image(user.getImage())
                .stars(user.getStars())
                .position(user.getPosition())
                .build();
    }
}
