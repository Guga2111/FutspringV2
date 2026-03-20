package com.futspring.backend.dto;

import com.futspring.backend.entity.Pelada;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeladaDetailResponseDTO {

    private Long id;
    private Long creatorId;
    private String name;
    private String dayOfWeek;
    private String timeOfDay;
    private Float duration;
    private String address;
    private String reference;
    private String image;
    private boolean autoCreateDailyEnabled;
    private int memberCount;
    private List<PeladaMemberDTO> members;

    public static PeladaDetailResponseDTO from(Pelada pelada) {
        List<PeladaMemberDTO> memberDTOs = pelada.getMembers().stream()
                .map(user -> PeladaMemberDTO.from(user, pelada.getAdmins().contains(user)))
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
                .members(memberDTOs)
                .build();
    }
}
