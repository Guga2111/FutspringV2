package com.futspring.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinalizeDailyRequestDTO {
    private List<Long> puskasWinnerIds;
    private List<Long> wiltballWinnerIds;
}
