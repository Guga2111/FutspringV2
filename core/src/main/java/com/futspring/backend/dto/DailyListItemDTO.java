package com.futspring.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.DailyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyListItemDTO {

    private Long id;
    private LocalDate dailyDate;
    private String dailyTime;
    private DailyStatus status;
    private int confirmedPlayerCount;
    @JsonProperty("isFinished")
    private boolean isFinished;

    public static DailyListItemDTO from(Daily daily) {
        return from(daily, daily.getConfirmedPlayers().size());
    }

    public static DailyListItemDTO from(Daily daily, int confirmedPlayerCount) {
        return DailyListItemDTO.builder()
                .id(daily.getId())
                .dailyDate(daily.getDailyDate())
                .dailyTime(daily.getDailyTime())
                .status(daily.getStatus())
                .confirmedPlayerCount(confirmedPlayerCount)
                .isFinished(daily.isFinished())
                .build();
    }
}
