package com.futspring.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePeladaRequestDTO {

    private String name;
    private String dayOfWeek;
    private String timeOfDay;
    private Float duration;
    private String address;
    private String reference;
    private Boolean autoCreateDailyEnabled;
}
