package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.DailyDetailDTO.MatchDTO;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO.PlayerDTO;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO.PlayerStatDTO;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO;
import com.futspring.backend.domain.daily.entity.Match;
import com.futspring.backend.domain.daily.entity.Team;
import com.futspring.backend.domain.user.User;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DailyDTOMapper {

    public PlayerDTO toPlayerDTO(User user) {
        return PlayerDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .image(user.getImage())
                .stars(user.getStars())
                .position(user.getPosition())
                .build();
    }

    public DailyDetailDTO.TeamDTO buildTeamDTO(Team team) {
        List<PlayerDTO> playerDTOs = team.getPlayers().stream()
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(this::toPlayerDTO)
                .toList();
        int totalStars = playerDTOs.stream().mapToInt(PlayerDTO::getStars).sum();
        double averageStars = playerDTOs.isEmpty() ? 0.0
                : Math.round(totalStars / (double) playerDTOs.size() * 100.0) / 100.0;
        return DailyDetailDTO.TeamDTO.builder()
                .id(team.getId())
                .name(team.getName())
                .totalStars(totalStars)
                .averageStars(averageStars)
                .color(team.getColor())
                .players(playerDTOs)
                .build();
    }

    public MatchDTO toMatchDTO(Match match, List<PlayerStatDTO> playerStats) {
        return MatchDTO.builder()
                .id(match.getId())
                .team1Id(match.getTeam1().getId())
                .team1Name(match.getTeam1().getName())
                .team2Id(match.getTeam2().getId())
                .team2Name(match.getTeam2().getName())
                .team1Score(match.getTeam1Score())
                .team2Score(match.getTeam2Score())
                .winnerId(match.getWinner() != null ? match.getWinner().getId() : null)
                .playerStats(playerStats)
                .build();
    }
}
