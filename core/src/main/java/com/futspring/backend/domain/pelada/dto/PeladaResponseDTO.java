package com.futspring.backend.domain.pelada.dto;

import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.pelada.Pelada;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    // whether the caller administers the pelada
    @JsonProperty("isAdmin")
    private boolean isAdmin;
    // next SCHEDULED/CONFIRMED session from today on; only filled by GET /peladas/my and /users/{id}/peladas
    private NextDailyDTO nextDaily;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NextDailyDTO {
        private Long id;
        private LocalDate date;
        private String time;
        private DailyStatus status;
        private int confirmedCount;
        // numberOfTeams × playersPerTeam
        private int capacity;
        // whether the caller confirmed attendance
        @JsonProperty("isConfirmed")
        private boolean isConfirmed;
    }

    /** Create/update responses: the caller is an admin (both routes require it). */
    public static PeladaResponseDTO forAdmin(Pelada pelada) {
        return from(pelada, pelada.getMembers().size(), true, null);
    }

    public static PeladaResponseDTO from(Pelada pelada, int memberCount, boolean isAdmin, NextDailyDTO nextDaily) {
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
                .isAdmin(isAdmin)
                .nextDaily(nextDaily)
                .build();
    }
}
