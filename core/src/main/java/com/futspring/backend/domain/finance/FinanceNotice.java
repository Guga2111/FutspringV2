package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;

@Entity
@Table(name = "finance_notices")
@Getter @Setter @NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class FinanceNotice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @ToString.Include
    private Long id;
    @Column(name = "pelada_id", nullable = false)
    private Long peladaId;
    @Column(name = "charge_id", nullable = false)
    private Long chargeId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = new BigDecimal("0.00");
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NoticeStatus status = NoticeStatus.PENDING;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "resolved_at", nullable = true)
    private Instant resolvedAt;
    @Column(name = "resolved_by", nullable = true)
    private Long resolvedBy;
    @Column(name = "reason", nullable = true, length = 1000)
    private String reason;
    @Column(name = "receipt_key", nullable = true, length = 100)
    private String receiptKey;
    @Column(name = "receipt_type", nullable = true, length = 30)
    private String receiptType;
    @Column(name = "receipt_expires_at", nullable = true)
    private Instant receiptExpiresAt;
    @Column(name = "receipt_deleted_at", nullable = true)
    private Instant receiptDeletedAt;

    @Override public boolean equals(Object other) {
        return other instanceof FinanceNotice entity && EntityIdentity.sameEntity(this, other, getId(), entity.getId());
    }
    @Override public int hashCode() { return EntityIdentity.effectiveClass(this).hashCode(); }
}
