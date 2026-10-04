package com.futspring.backend.domain.daily.dto;

import com.futspring.backend.domain.daily.entity.DailyStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDailyStatusRequestDTO {

    @NotNull(message = "O status é obrigatório")
    private DailyStatus status;
}
