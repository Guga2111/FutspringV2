package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;

@Entity
@Table(name = "finance_audit")
@Getter @Setter @NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class FinanceAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @ToString.Include
    private Long id;
    @Column(name = "pelada_id", nullable = false)
    private Long peladaId;
    @Column(name = "actor_id", nullable = false)
    private Long actorId;
    @Column(name = "actor_name", nullable = false, length = 200)
    private String actorName;
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 100)
    private AuditOperation operation;
    @Column(name = "target", nullable = false, length = 100)
    private String target;
    @Column(name = "reason", nullable = true, length = 1000)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "before_data", nullable = true, length = 1000000)
    private String beforeData;
    @Column(name = "after_data", nullable = true, length = 1000000)
    private String afterData;
    @Column(name = "request_key", nullable = true)
    private UUID requestKey;
    @Column(name = "request_hash", nullable = true, length = 64)
    private String requestHash;

    @Column(name = "result_list_id")
    private Long resultListId;
    @Override public boolean equals(Object other) {
        return other instanceof FinanceAudit entity && EntityIdentity.sameEntity(this, other, getId(), entity.getId());
    }
    @Override public int hashCode() { return EntityIdentity.effectiveClass(this).hashCode(); }
}
