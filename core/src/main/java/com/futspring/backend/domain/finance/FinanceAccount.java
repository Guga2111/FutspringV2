package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;

@Entity
@Table(name = "finance_accounts")
@Getter @Setter @NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class FinanceAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @ToString.Include
    private Long id;
    @Column(name = "pelada_id", nullable = false)
    private Long peladaId;
    @Column(name = "revision", nullable = false)
    private long revision;
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility = Visibility.INDIVIDUAL;
    @Column(name = "pix_key", nullable = true, length = 200)
    private String pixKey;
    @Column(name = "pix_recipient", nullable = true, length = 200)
    private String pixRecipient;
    @Column(name = "qr_id", nullable = true)
    private Long qrId;

    @Override public boolean equals(Object other) {
        return other instanceof FinanceAccount entity && EntityIdentity.sameEntity(this, other, getId(), entity.getId());
    }
    @Override public int hashCode() { return EntityIdentity.effectiveClass(this).hashCode(); }
}
