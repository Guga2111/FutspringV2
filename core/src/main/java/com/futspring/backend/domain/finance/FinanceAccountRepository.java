package com.futspring.backend.domain.finance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinanceAccountRepository extends JpaRepository<FinanceAccount, Long> {
    Optional<FinanceAccount> findByPeladaId(Long peladaId);
    @Modifying
    @Query("update FinanceAccount a set a.revision = a.revision + 1 where a.peladaId in :peladaIds")
    void incrementRevisions(@Param("peladaIds") Collection<Long> peladaIds);
}
