package com.futspring.backend.domain.daily.entity;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// A diarista who lost their spot to a mensalista; shown to them until they dismiss it (seenAt)
@Entity
@Table(name = "daily_attendance_bumps")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyAttendanceBump {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_id", nullable = false)
    private Daily daily;

    // the diarista who lost the spot
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // the mensalista who took it
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bumped_by_id", nullable = false)
    private User bumpedBy;

    @Column(name = "bumped_at", nullable = false)
    private Instant bumpedAt;

    @Column(name = "seen_at")
    private Instant seenAt;

    @Override
    public boolean equals(Object o) {
        return o instanceof DailyAttendanceBump other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
