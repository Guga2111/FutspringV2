package com.futspring.backend.domain.tour;

import com.futspring.backend.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// A tour the user has finished or closed; it isn't started automatically again
@Entity
@Table(name = "user_tours")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class UserTour {

    @EmbeddedId
    @ToString.Include
    private UserTourId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    public UserTour(User user, TourKey tour, Instant completedAt) {
        this.id = new UserTourId(user.getId(), tour);
        this.user = user;
        this.completedAt = completedAt;
    }

    public TourKey getTour() {
        return id.getTour();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserTour other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
