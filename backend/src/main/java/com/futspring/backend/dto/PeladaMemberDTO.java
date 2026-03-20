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
public class PeladaMemberDTO {

    private Long id;
    private String username;
    private String image;
    private int stars;
    private String position;
    private boolean isAdmin;

    public static PeladaMemberDTO from(User user, boolean isAdmin) {
        return PeladaMemberDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .image(user.getImage())
                .stars(user.getStars())
                .position(user.getPosition())
                .isAdmin(isAdmin)
                .build();
    }
}
