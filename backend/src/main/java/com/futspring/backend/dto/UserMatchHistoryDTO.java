package com.futspring.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMatchHistoryDTO {

    private List<MatchRow> rows;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchRow {
        private Long dailyId;
        private LocalDate date;
        private Long peladaId;
        private String peladaName;
        private int goals;
        private int assists;
        private int matchesPlayed;
        private int wins;
        private String result;
    }
}
