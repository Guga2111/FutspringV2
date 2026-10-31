package com.futspring.backend.domain.daily.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CreateDailyRequestDTO(
        @NotNull(message = "A data é obrigatória")
        LocalDate dailyDate,

        @NotBlank(message = "O horário é obrigatório")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$", message = "Horário inválido (HH:mm)")
        String dailyTime) {
}
