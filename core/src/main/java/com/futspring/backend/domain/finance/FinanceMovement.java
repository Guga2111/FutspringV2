package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;

@Entity
@Table(name = "finance_movements")
@Getter @Setter @NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class FinanceMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @ToString.Include
    private Long id;
    @Column(name = "pelada_id", nullable = false)
    private Long peladaId;
    @Column(name = "list_id", nullable = true)
    private Long listId;
    @Column(name = "daily_id", nullable = true)
    private Long dailyId;
    @Column(name = "daily_label", nullable = true, length = 200)
    private String dailyLabel;
    @Column(name = "charge_id", nullable = true)
    private Long chargeId;
    @Column(name = "notice_id", nullable = true)
    private Long noticeId;
    @Column(name = "original_id", nullable = true)
    private Long originalId;
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private MovementKind kind = MovementKind.INCOME;
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = new BigDecimal("0.00");
    @Column(name = "obligation_delta", nullable = false, precision = 14, scale = 2)
    private BigDecimal obligationDelta = new BigDecimal("0.00");
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private PaymentMethod method = PaymentMethod.OTHER;
    @Column(name = "description", nullable = false, length = 500)
    private String description;
    @Column(name = "category", nullable = true, length = 100)
    private String category;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override public boolean equals(Object other) {
        return other instanceof FinanceMovement entity && EntityIdentity.sameEntity(this, other, getId(), entity.getId());
    }
    @Override public int hashCode() { return EntityIdentity.effectiveClass(this).hashCode(); }
}
