package com.futspring.backend.domain.daily.dto;

import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

// A diarista (user) who lost the spot to a mensalista (bumpedBy); public fields only
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceBumpDTO {

    private PublicUserDTO user;
    private PublicUserDTO bumpedBy;
    private Instant bumpedAt;

    public static AttendanceBumpDTO from(DailyAttendanceBump bump) {
        return AttendanceBumpDTO.builder()
                .user(PublicUserDTO.from(bump.getUser()))
                .bumpedBy(PublicUserDTO.from(bump.getBumpedBy()))
                .bumpedAt(bump.getBumpedAt())
                .build();
    }
}
