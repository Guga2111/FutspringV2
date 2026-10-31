package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "pelada_invitations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString(onlyExplicitlyIncluded = true)
public class PeladaInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @ToString.Include
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pelada_id", nullable = false)
    private Pelada pelada;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "responded_at")
    private Instant respondedAt;
    @Column(name = "canceled_at")
    private Instant canceledAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvitationStatus status;

    public InvitationStatus statusAt(Instant now) {
        return status == InvitationStatus.PENDING && !now.isBefore(expiresAt) ? InvitationStatus.EXPIRED : status;
    }

    @Override public boolean equals(Object o) {
        return o instanceof PeladaInvitation other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }
    @Override public int hashCode() { return EntityIdentity.effectiveClass(this).hashCode(); }
}
