package com.futspring.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePeladaRequestDTO {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Day of week is required")
    private String dayOfWeek;

    @NotBlank(message = "Time of day is required")
    private String timeOfDay;

    @NotNull(message = "Duration is required")
    private Float duration;

    private String address;
    private String reference;
    private boolean autoCreateDailyEnabled = false;
}
