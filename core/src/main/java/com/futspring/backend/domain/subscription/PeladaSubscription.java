package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// One per pelada; it follows the pelada on a creator transfer
@Entity
@Table(name = "pelada_subscriptions")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class PeladaSubscription {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pelada_id", nullable = false, unique = true)
    private Pelada pelada;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private SubscriptionKind kind;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", length = 20)
    private SubscriptionPlan plan;

    @Column(name = "founder", nullable = false)
    private boolean founder;

    @Column(name = "pix_claimed_at")
    private Instant pixClaimedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "claimed_plan", length = 20)
    private SubscriptionPlan claimedPlan;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PeladaSubscription(Pelada pelada, SubscriptionKind kind, Instant validUntil, Instant now) {
        this.pelada = pelada;
        this.kind = kind;
        this.validUntil = validUntil;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PeladaSubscription other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
