package com.futspring.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinalizeDailyRequestDTO {
    private Long puskasWinnerId;
    private Long wiltballWinnerId;
}
