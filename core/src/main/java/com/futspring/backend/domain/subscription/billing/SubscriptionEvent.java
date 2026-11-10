package com.futspring.backend.domain.subscription.billing;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.entity.EntityIdentity;
import com.futspring.backend.domain.subscription.SubscriptionKind;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// One change to a pelada's subscription, for the platform admin's history; deleted with the pelada
@Entity
@Table(name = "subscription_events")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class SubscriptionEvent {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pelada_id", nullable = false)
    private Long peladaId;

    // null = the system
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private SubscriptionEventType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind_before", length = 20)
    private SubscriptionKind kindBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind_after", length = 20)
    private SubscriptionKind kindAfter;

    @Column(name = "valid_until_before")
    private Instant validUntilBefore;

    @Column(name = "valid_until_after")
    private Instant validUntilAfter;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        return o instanceof SubscriptionEvent other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
