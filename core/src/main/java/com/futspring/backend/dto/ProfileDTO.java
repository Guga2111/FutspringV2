package com.futspring.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.futspring.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    // email is only returned to the profile owner
    public static ProfileDTO from(User user, boolean isSelf) {
        return ProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(isSelf ? user.getEmail() : null)
                .image(user.getImage())
                .backgroundImage(user.getBackgroundImage())
                .stars(user.getStars())
                .position(user.getPosition())
                .build();
    }
}
