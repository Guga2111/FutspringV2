package com.futspring.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinalizeDailyRequestDTO {

    // optional; null means no winners for the award
    private List<@NotNull Long> puskasWinnerIds;

    private List<@NotNull Long> wiltballWinnerIds;
}
