package com.futspring.backend.dto;

import com.futspring.backend.entity.Daily;
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
    private String status;
    private int confirmedPlayerCount;
    private boolean isFinished;

    public static DailyListItemDTO from(Daily daily) {
        return DailyListItemDTO.builder()
                .id(daily.getId())
                .dailyDate(daily.getDailyDate())
                .dailyTime(daily.getDailyTime())
                .status(daily.getStatus())
                .confirmedPlayerCount(daily.getConfirmedPlayers().size())
                .isFinished(daily.isFinished())
                .build();
    }
}
