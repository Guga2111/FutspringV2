package com.futspring.backend.domain.finance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinanceChargeRepository extends JpaRepository<FinanceCharge, Long> {
    @EntityGraph(attributePaths = "components")
    List<FinanceCharge> findByListIdOrderById(Long listId);
    Optional<FinanceCharge> findByIdAndPeladaIdAndListId(Long id, Long peladaId, Long listId);
    boolean existsByListIdAndPersonIdAndCanceledFalse(Long listId, Long personId);
    List<FinanceCharge> findByListIdIn(Collection<Long> listIds);
}
