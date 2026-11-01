package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;

@Entity
@Table(name = "finance_templates")
@Getter @Setter @NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class FinanceTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @ToString.Include
    private Long id;
    @Column(name = "pelada_id", nullable = false)
    private Long peladaId;
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false)
    private Classification classification = Classification.OTHER;
    @Column(name = "description", nullable = true, length = 2000)
    private String description;
    @Column(name = "pix_key", nullable = true, length = 200)
    private String pixKey;
    @Column(name = "pix_recipient", nullable = true, length = 200)
    private String pixRecipient;
    @Column(name = "qr_id", nullable = true)
    private Long qrId;
    @Column(name = "archived", nullable = false)
    private boolean archived;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "finance_template_components", joinColumns = @JoinColumn(name = "owner_id"))
    @OrderColumn(name = "position")
    private java.util.List<PriceComponent> components = new ArrayList<>();

    @Override public boolean equals(Object other) {
        return other instanceof FinanceTemplate entity && EntityIdentity.sameEntity(this, other, getId(), entity.getId());
    }
    @Override public int hashCode() { return EntityIdentity.effectiveClass(this).hashCode(); }
}
