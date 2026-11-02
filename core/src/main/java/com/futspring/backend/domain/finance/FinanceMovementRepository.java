package com.futspring.backend.domain.finance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinanceMovementRepository extends JpaRepository<FinanceMovement, Long> {
    Page<FinanceMovement> findByPeladaIdOrderByIdDesc(Long peladaId, Pageable page);
    List<FinanceMovement> findByChargeIdIn(Collection<Long> ids);
    Optional<FinanceMovement> findByIdAndPeladaId(Long id, Long peladaId);
    boolean existsByOriginalId(Long id);
    boolean existsByPeladaId(Long peladaId);
    @Query("SELECT COUNT(m) > 0 FROM FinanceMovement m WHERE m.peladaId = :id AND m.kind = 'OPENING' AND NOT EXISTS (SELECT r.id FROM FinanceMovement r WHERE r.originalId = m.id)")
    boolean hasActiveOpening(@Param("id") Long id);
    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM FinanceMovement m WHERE m.peladaId = :id")
    BigDecimal balance(@Param("id") Long id);
    @Query("SELECT m.originalId FROM FinanceMovement m WHERE m.originalId IN :ids")
    Set<Long> reversedIds(@Param("ids") Collection<Long> ids);
    List<FinanceMovement> findByDailyId(Long id);
}
