package com.futspring.backend.domain.finance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinanceManagerRepository extends JpaRepository<FinanceManager, Long> {
    boolean existsByPeladaIdAndUserId(Long peladaId, Long userId);
    List<FinanceManager> findByPeladaId(Long peladaId);
    void deleteByPeladaIdAndUserIdIn(Long peladaId, Collection<Long> userIds);
}
