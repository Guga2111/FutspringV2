package com.futspring.backend.domain.stats.dto;

import com.futspring.backend.domain.stats.entity.Ranking;
import com.futspring.backend.domain.user.User;
import lombok.Builder;

@Builder
public record RankingDTO(
        Long userId,
        String username,
        String userImage,
        int goals,
        int assists,
        int matchesPlayed,
        int wins) {

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
