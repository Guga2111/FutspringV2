package com.futspring.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDailyRequestDTO {

    @NotNull(message = "A data é obrigatória")
    private LocalDate dailyDate;

    @NotBlank(message = "O horário é obrigatório")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$", message = "Horário inválido (HH:mm)")
    private String dailyTime;
}
