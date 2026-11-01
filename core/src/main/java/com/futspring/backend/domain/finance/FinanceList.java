package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;

@Entity
@Table(name = "finance_lists")
@Getter @Setter @NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class FinanceList {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @ToString.Include
    private Long id;
    @Column(name = "pelada_id", nullable = false)
    private Long peladaId;
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false)
    private Classification classification = Classification.OTHER;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ListStatus status = ListStatus.DRAFT;
    @Column(name = "description", nullable = true, length = 2000)
    private String description;
    @Column(name = "due_date", nullable = true)
    private LocalDate dueDate;
    @Column(name = "daily_id", nullable = true)
    private Long dailyId;
    @Column(name = "daily_label", nullable = true, length = 200)
    private String dailyLabel;
    @Column(name = "pix_key", nullable = true, length = 200)
    private String pixKey;
    @Column(name = "pix_recipient", nullable = true, length = 200)
    private String pixRecipient;
    @Column(name = "qr_id", nullable = true)
    private Long qrId;
    @Column(name = "published_at", nullable = true)
    private Instant publishedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "finance_list_components", joinColumns = @JoinColumn(name = "owner_id"))
    @OrderColumn(name = "position")
    private java.util.List<PriceComponent> components = new ArrayList<>();

    @Override public boolean equals(Object other) {
        return other instanceof FinanceList entity && EntityIdentity.sameEntity(this, other, getId(), entity.getId());
    }
    @Override public int hashCode() { return EntityIdentity.effectiveClass(this).hashCode(); }
}
