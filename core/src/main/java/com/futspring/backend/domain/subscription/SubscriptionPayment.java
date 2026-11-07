package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// A payment recorded by the platform admin. Outlives the pelada (pelada_id SET NULL), so the names are snapshots
@Entity
@Table(name = "subscription_payments")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class SubscriptionPayment {

    @Id
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A plain id: the pelada may be deleted and the payment stays
    @Column(name = "pelada_id")
    private Long peladaId;

    @Column(name = "pelada_name", nullable = false)
    private String peladaName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id")
    private User payer;

    @Column(name = "payer_name", nullable = false)
    private String payerName;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private SubscriptionPlan plan;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_id", nullable = false)
    private User recordedBy;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean isVoided() {
        return voidedAt != null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SubscriptionPayment other && EntityIdentity.sameEntity(this, o, getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }
}
