package com.futspring.backend.domain.finance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinanceAuditRepository extends JpaRepository<FinanceAudit, Long> {
    List<FinanceAudit> findByPeladaId(Long id);
    Optional<FinanceAudit> findByPeladaIdAndRequestKey(Long id, UUID key);
    Slice<FinanceAudit> findByPeladaIdOrderByIdDesc(Long id, Pageable page);
}
