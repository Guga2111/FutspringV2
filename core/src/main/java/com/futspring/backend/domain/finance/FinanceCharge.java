package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;

@Entity
@Table(name = "finance_charges")
@Getter @Setter @NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class FinanceCharge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @ToString.Include
    private Long id;
    @Column(name = "pelada_id", nullable = false)
    private Long peladaId;
    @Column(name = "list_id", nullable = false)
    private Long listId;
    @Column(name = "person_id", nullable = false)
    private Long personId;
    @Column(name = "person_name", nullable = false, length = 200)
    private String personName;
    @Column(name = "user_id", nullable = true)
    private Long userId;
    @Column(name = "adjustment", nullable = false, precision = 14, scale = 2)
    private BigDecimal adjustment = new BigDecimal("0.00");
    @Column(name = "amount_due", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountDue = new BigDecimal("0.00");
    @Column(name = "due_date", nullable = true)
    private LocalDate dueDate;
    @Column(name = "canceled", nullable = false)
    private boolean canceled;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "finance_charge_components", joinColumns = @JoinColumn(name = "owner_id"))
    @OrderColumn(name = "position")
    private java.util.List<PriceComponent> components = new ArrayList<>();

    @Override public boolean equals(Object other) {
        return other instanceof FinanceCharge entity && EntityIdentity.sameEntity(this, other, getId(), entity.getId());
    }
    @Override public int hashCode() { return EntityIdentity.effectiveClass(this).hashCode(); }
}
