package com.futspring.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePeladaRequestDTO {

    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 100, message = "Máximo de 100 caracteres")
    private String name;

    @NotBlank(message = "O dia da semana é obrigatório")
    @Pattern(regexp = DayOfWeekPattern.REGEX, message = DayOfWeekPattern.MESSAGE)
    private String dayOfWeek;

    @NotBlank(message = "O horário é obrigatório")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$", message = "Horário inválido (HH:mm)")
    private String timeOfDay;

    @NotNull(message = "A duração é obrigatória")
    @Positive(message = "A duração deve ser maior que zero")
    private Float duration;

    @Size(max = 255, message = "Máximo de 255 caracteres")
    private String address;

    @Size(max = 255, message = "Máximo de 255 caracteres")
    private String reference;

    private boolean autoCreateDailyEnabled = false;

    @Min(value = 2, message = "Mínimo de 2 times")
    @Max(value = 10, message = "Máximo de 10 times")
    private int numberOfTeams = 2;

    @Min(value = 2, message = "Mínimo de 2 jogadores por time")
    @Max(value = 20, message = "Máximo de 20 jogadores por time")
    private int playersPerTeam = 5;
}
