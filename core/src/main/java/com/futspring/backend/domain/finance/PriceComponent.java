package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.entity.EntityIdentity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PriceComponent {
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
