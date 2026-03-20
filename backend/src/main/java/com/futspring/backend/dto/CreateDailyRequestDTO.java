package com.futspring.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDailyRequestDTO {

    @NotNull
    private LocalDate dailyDate;

    @NotBlank
    private String dailyTime;
}
