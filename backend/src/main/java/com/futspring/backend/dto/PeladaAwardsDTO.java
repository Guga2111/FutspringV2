package com.futspring.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeladaAwardsDTO {

    private int totalCategories;
    private int totalAwardsDistributed;
    private List<AwardCategoryDTO> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AwardCategoryDTO {
        private String type;
        private String name;
        private String description;
        private List<AwardWinnerDTO> topWinners;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AwardWinnerDTO {
        private Long userId;
        private String username;
        private String userImage;
        private int count;
    }
}
