package com.futspring.backend.domain.daily.dto;

import com.futspring.backend.domain.daily.entity.DailyStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDailyStatusRequestDTO(
        @NotNull(message = "O status é obrigatório")
        DailyStatus status) {
}
