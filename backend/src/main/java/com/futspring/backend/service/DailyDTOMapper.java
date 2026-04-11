package com.futspring.backend.service;

import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.entity.Team;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DailyDTOMapper {

    public DailyDetailDTO.TeamDTO buildTeamDTO(Team team) {
        List<DailyDetailDTO.PlayerDTO> playerDTOs = team.getPlayers().stream()
                .map(u -> DailyDetailDTO.PlayerDTO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .image(u.getImage())
                        .stars(u.getStars())
                        .position(u.getPosition())
                        .build())
                .collect(Collectors.toList());
        int totalStars = playerDTOs.stream().mapToInt(DailyDetailDTO.PlayerDTO::getStars).sum();
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
}
