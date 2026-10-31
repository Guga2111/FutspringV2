package com.futspring.backend.domain.daily.dto;

import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import lombok.Builder;

import java.time.Instant;

// A diarista (user) who lost the spot to a mensalista (bumpedBy); public fields only
@Builder
public record AttendanceBumpDTO(
        PublicUserDTO user,
        PublicUserDTO bumpedBy,
        Instant bumpedAt) {

    public static AttendanceBumpDTO from(DailyAttendanceBump bump) {
        return AttendanceBumpDTO.builder()
                .user(PublicUserDTO.from(bump.getUser()))
                .bumpedBy(PublicUserDTO.from(bump.getBumpedBy()))
                .bumpedAt(bump.getBumpedAt())
                .build();
    }
}
