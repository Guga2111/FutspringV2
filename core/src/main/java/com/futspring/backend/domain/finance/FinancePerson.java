package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;

@Entity
@Table(name = "finance_people")
@Getter @Setter @NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class FinancePerson {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @ToString.Include
    private Long id;
    @Column(name = "pelada_id", nullable = false)
    private Long peladaId;
    @Column(name = "user_id", nullable = true)
    private Long userId;
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Override public boolean equals(Object other) {
        return other instanceof FinancePerson entity && EntityIdentity.sameEntity(this, other, getId(), entity.getId());
    }
    @Override public int hashCode() { return EntityIdentity.effectiveClass(this).hashCode(); }
}
