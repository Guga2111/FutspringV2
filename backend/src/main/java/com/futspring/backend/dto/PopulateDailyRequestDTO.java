package com.futspring.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class PopulateDailyRequestDTO {

    private List<ParsedTeamDTO> teams;
    private List<ParsedMatchDTO> matches;

    @Data
    public static class ParsedTeamDTO {
        private String colorName;
        private String colorHex;
        private List<ParsedPlayerDTO> players;
    }

    @Data
    public static class ParsedPlayerDTO {
        private Long userId;
        private int totalGoals;
        private int totalAssists;
    }

    @Data
    public static class ParsedMatchDTO {
        private String team1ColorName;
        private int team1Score;
        private String team2ColorName;
        private int team2Score;
    }
}
