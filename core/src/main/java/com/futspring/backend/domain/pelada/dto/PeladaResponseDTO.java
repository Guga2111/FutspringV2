package com.futspring.backend.domain.pelada.dto;

import com.futspring.backend.domain.daily.dto.AttendanceBumpDTO;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.pelada.Pelada;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

@Builder
public record PeladaResponseDTO(
        Long id,
        String name,
        String dayOfWeek,
        String timeOfDay,
        Float duration,
        String address,
        String reference,
        String image,
        boolean autoCreateDailyEnabled,
        int memberCount,
        int numberOfTeams,
        int playersPerTeam,
        // IANA zone of the sessions (the frontend shows startsAt / confirmOpensAt in it)
        String timeZone,
        // whether the caller administers the pelada
        @JsonProperty("isAdmin")
        boolean isAdmin,
        // next SCHEDULED/CONFIRMED session from today on; only filled by GET /peladas/my and /users/{id}/peladas
        NextDailyDTO nextDaily) {

    @Builder
    public record NextDailyDTO(
            Long id,
            LocalDate date,
            String time,
            DailyStatus status,
            int confirmedCount,
            // numberOfTeams × playersPerTeam
            int capacity,
            // whether the caller confirmed attendance
            @JsonProperty("isConfirmed")
            boolean isConfirmed,
            // session start, in the pelada's zone
            Instant startsAt,
            // from when the caller may confirm themselves; null for mensalistas. The server re-checks it
            Instant confirmOpensAt,
            // the caller's bump in this session not dismissed yet, or null
            AttendanceBumpDTO bump) {
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
                .timeZone(pelada.getTimeZone())
                .isAdmin(isAdmin)
                .nextDaily(nextDaily)
                .build();
    }
}
