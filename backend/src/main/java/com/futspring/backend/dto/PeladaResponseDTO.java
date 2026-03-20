package com.futspring.backend.dto;

import com.futspring.backend.entity.Pelada;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public static PeladaResponseDTO from(Pelada pelada) {
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
                .memberCount(pelada.getMembers().size())
                .build();
    }
}
