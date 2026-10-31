package com.futspring.backend.domain.pelada.dto;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaPlayerStars;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Builder
public record PeladaDetailResponseDTO(
        Long id,
        Long creatorId,
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
        String timeZone,
        int maxMensalistas,
        List<PeladaMemberDTO> members) {

    // starsByUserId: PeladaPlayerStarsRepository.findStarsByPelada; members missing from it have the default.
    // mensalistaIds: PeladaMensalistaRepository.findUserIdsByPelada
    public static PeladaDetailResponseDTO from(Pelada pelada, Map<Long, Integer> starsByUserId, Set<Long> mensalistaIds) {
        List<PeladaMemberDTO> memberDTOs = pelada.getMembers().stream()
                .map(user -> PeladaMemberDTO.from(user, pelada.getAdmins().contains(user),
                        starsByUserId.getOrDefault(user.getId(), PeladaPlayerStars.DEFAULT_STARS),
                        mensalistaIds.contains(user.getId())))
                .collect(Collectors.toList());

        return PeladaDetailResponseDTO.builder()
                .id(pelada.getId())
                .creatorId(pelada.getCreator() != null ? pelada.getCreator().getId() : null)
                .name(pelada.getName())
                .dayOfWeek(pelada.getDayOfWeek())
                .timeOfDay(pelada.getTimeOfDay())
                .duration(pelada.getDuration())
                .address(pelada.getAddress())
                .reference(pelada.getReference())
                .image(pelada.getImage())
                .autoCreateDailyEnabled(pelada.isAutoCreateDailyEnabled())
                .memberCount(pelada.getMembers().size())
                .numberOfTeams(pelada.getNumberOfTeams())
                .playersPerTeam(pelada.getPlayersPerTeam())
                .timeZone(pelada.getTimeZone())
                .maxMensalistas(pelada.getMaxMensalistas())
                .members(memberDTOs)
                .build();
    }
}
