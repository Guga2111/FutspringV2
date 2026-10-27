package com.futspring.backend.domain.pelada.dto;

import com.futspring.backend.domain.user.User;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isAdmin")
    private boolean isAdmin;

    // stars: the member's stars in this pelada
    public static PeladaMemberDTO from(User user, boolean isAdmin, int stars) {
        return PeladaMemberDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .image(user.getImage())
                .stars(stars)
                .position(user.getPosition())
                .isAdmin(isAdmin)
                .build();
    }
}
