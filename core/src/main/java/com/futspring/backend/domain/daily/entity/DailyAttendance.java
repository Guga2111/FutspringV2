package com.futspring.backend.domain.daily.entity;

import com.futspring.backend.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// A player confirmed in a session, and when (a mensalista who confirms into a full session bumps the diarista
// confirmed last). Maps the legacy daily_confirmed_players join table, whose PK is (daily_id, user_id)
@Entity
@Table(name = "daily_confirmed_players")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class DailyAttendance {

    @EmbeddedId
    @ToString.Include
    private DailyAttendanceId id;

    @MapsId("dailyId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_id", nullable = false)
    private Daily daily;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    public DailyAttendance(Daily daily, User user, Instant confirmedAt) {
        this.id = new DailyAttendanceId(daily.getId(), user.getId());
        this.daily = daily;
        this.user = user;
        this.confirmedAt = confirmedAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DailyAttendance other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
