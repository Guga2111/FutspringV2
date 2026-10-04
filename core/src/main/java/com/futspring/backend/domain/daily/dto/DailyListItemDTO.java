package com.futspring.backend.domain.daily.dto;

import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// Built by DailyListItemAssembler (counts come from grouped queries, isConfirmed is the caller's attendance)
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
    private int teamCount;
    private int matchCount;
    private String championImage;
    @JsonProperty("isConfirmed")
    private boolean isConfirmed;
}
