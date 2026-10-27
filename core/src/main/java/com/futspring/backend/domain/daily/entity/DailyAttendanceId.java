package com.futspring.backend.domain.daily.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DailyAttendanceId implements Serializable {

    @Column(name = "daily_id")
    private Long dailyId;

    @Column(name = "user_id")
    private Long userId;
}
