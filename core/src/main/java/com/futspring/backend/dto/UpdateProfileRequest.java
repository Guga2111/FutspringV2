package com.futspring.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 3, max = 30)
    private String username;

    private String position;

    @Min(1)
    @Max(5)
    private Integer stars;
}
