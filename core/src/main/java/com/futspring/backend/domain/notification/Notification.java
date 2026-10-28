package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// One notification of one recipient. Only typed references are stored; the text is rendered from them.
@Entity
@Table(name = "notifications")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pelada_id")
    private Pelada pelada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_id")
    private Daily daily;

    // who caused it; null for system events (scheduler, awards)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    // only on AWARD_WON
    @Enumerated(EnumType.STRING)
    @Column(name = "award", length = 20)
    private NotificationAward award;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // null = unread
    @Column(name = "read_at")
    private Instant readAt;

    @Override
    public boolean equals(Object o) {
        return o instanceof Notification other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
