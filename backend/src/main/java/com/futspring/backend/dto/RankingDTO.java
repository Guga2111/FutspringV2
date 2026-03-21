package com.futspring.backend.dto;

import com.futspring.backend.entity.Ranking;
import com.futspring.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RankingDTO {

    private Long userId;
    private String username;
    private String userImage;
    private int goals;
    private int assists;
    private int matchesPlayed;
    private int wins;

    public static RankingDTO fromRanking(Ranking ranking) {
        User user = ranking.getUser();
        return RankingDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .userImage(user.getImage())
                .goals(ranking.getGoals())
                .assists(ranking.getAssists())
                .matchesPlayed(ranking.getMatchesPlayed())
                .wins(ranking.getWins())
                .build();
    }

    public static RankingDTO fromUser(User user) {
        return RankingDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .userImage(user.getImage())
                .goals(0)
                .assists(0)
                .matchesPlayed(0)
                .wins(0)
                .build();
    }
}
