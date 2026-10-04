package com.futspring.backend.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Partial update: null fields are left unchanged
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePeladaRequestDTO {

    @Size(min = 1, max = 100, message = "O nome deve ter entre 1 e 100 caracteres")
    private String name;

    @Pattern(regexp = DayOfWeekPattern.REGEX, message = DayOfWeekPattern.MESSAGE)
    private String dayOfWeek;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$", message = "Horário inválido (HH:mm)")
    private String timeOfDay;

    @Positive(message = "A duração deve ser maior que zero")
    private Float duration;

    @Size(max = 255, message = "Máximo de 255 caracteres")
    private String address;

    @Size(max = 255, message = "Máximo de 255 caracteres")
    private String reference;

    private Boolean autoCreateDailyEnabled;
}
