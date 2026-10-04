package com.futspring.backend.domain.pelada.dto;

import com.futspring.backend.domain.pelada.Pelada;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeladaResponseDTO {

    private Long id;
    private String name;
    private String dayOfWeek;
    private String timeOfDay;
    private Float duration;
    private String address;
    private String reference;
    private String image;
    private boolean autoCreateDailyEnabled;
    private int memberCount;
    private int numberOfTeams;
    private int playersPerTeam;
    // next SCHEDULED/CONFIRMED session from today on; only filled by GET /peladas/my and /users/{id}/peladas
    private LocalDate nextDailyDate;

    public static PeladaResponseDTO from(Pelada pelada) {
        return from(pelada, pelada.getMembers().size(), null);
    }

    public static PeladaResponseDTO from(Pelada pelada, int memberCount, LocalDate nextDailyDate) {
        return PeladaResponseDTO.builder()
                .id(pelada.getId())
                .name(pelada.getName())
                .dayOfWeek(pelada.getDayOfWeek())
                .timeOfDay(pelada.getTimeOfDay())
                .duration(pelada.getDuration())
                .address(pelada.getAddress())
                .reference(pelada.getReference())
                .image(pelada.getImage())
                .autoCreateDailyEnabled(pelada.isAutoCreateDailyEnabled())
                .memberCount(memberCount)
                .numberOfTeams(pelada.getNumberOfTeams())
                .playersPerTeam(pelada.getPlayersPerTeam())
                .nextDailyDate(nextDailyDate)
                .build();
    }
}
