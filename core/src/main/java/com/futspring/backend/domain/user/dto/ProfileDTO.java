package com.futspring.backend.domain.user.dto;

import com.futspring.backend.domain.user.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record ProfileDTO(
        Long id,
        String username,
        String email,
        String image,
        String backgroundImage,
        // Rounded average of the user's stars in their peladas; null (omitted) when they have none
        Integer stars,
        String position) {

    // email is only returned to the profile owner
    public static ProfileDTO from(User user, boolean isSelf, Integer stars) {
        return ProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(isSelf ? user.getEmail() : null)
                .image(user.getImage())
                .backgroundImage(user.getBackgroundImage())
                .stars(stars)
                .position(user.getPosition())
                .build();
    }
}
