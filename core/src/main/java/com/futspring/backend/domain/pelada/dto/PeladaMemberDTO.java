package com.futspring.backend.domain.pelada.dto;

import com.futspring.backend.domain.user.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record PeladaMemberDTO(
        Long id,
        String username,
        String image,
        int stars,
        String position,
        @JsonProperty("isAdmin")
        boolean isAdmin,
        @JsonProperty("isMensalista")
        boolean isMensalista) {

    // stars: the member's stars in this pelada
    public static PeladaMemberDTO from(User user, boolean isAdmin, int stars, boolean isMensalista) {
        return PeladaMemberDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .image(user.getImage())
                .stars(stars)
                .position(user.getPosition())
                .isAdmin(isAdmin)
                .isMensalista(isMensalista)
                .build();
    }
}
