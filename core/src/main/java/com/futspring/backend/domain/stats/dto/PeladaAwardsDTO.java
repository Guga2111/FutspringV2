package com.futspring.backend.domain.stats.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PeladaAwardsDTO(
        int totalCategories,
        int totalAwardsDistributed,
        List<AwardCategoryDTO> categories) {

    @Builder
    public record AwardCategoryDTO(
            String type,
            String name,
            String description,
            List<AwardWinnerDTO> topWinners) {
    }

    @Builder
    public record AwardWinnerDTO(
            Long userId,
            String username,
            String userImage,
            int count) {
    }
}
