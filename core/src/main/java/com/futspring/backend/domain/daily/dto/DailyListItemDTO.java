package com.futspring.backend.domain.daily.dto;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
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
