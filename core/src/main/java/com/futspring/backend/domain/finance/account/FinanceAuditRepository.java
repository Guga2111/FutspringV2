package com.futspring.backend.domain.finance.account;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import com.futspring.backend.domain.finance.FinanceTypes;
public interface FinanceAuditRepository extends JpaRepository<FinanceAudit, Long> {
    List<FinanceAudit> findByPeladaId(Long id);
    Optional<FinanceAudit> findByPeladaIdAndRequestKey(Long id, UUID key);
    Page<FinanceAudit> findByPeladaIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndOperationInOrderByIdDesc(
            Long id, Instant from, Instant to, Collection<FinanceTypes.AuditOperation> operations, Pageable page);
}
