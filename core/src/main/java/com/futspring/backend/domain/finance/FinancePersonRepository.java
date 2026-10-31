package com.futspring.backend.domain.finance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinancePersonRepository extends JpaRepository<FinancePerson, Long> {
    List<FinancePerson> findByPeladaId(Long id);
    Optional<FinancePerson> findByPeladaIdAndUserId(Long peladaId, Long userId);
    Optional<FinancePerson> findByIdAndPeladaId(Long id, Long peladaId);
}
