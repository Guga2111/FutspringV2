package com.futspring.backend.domain.finance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinanceQrRepository extends JpaRepository<FinanceQr, Long> {
    boolean existsByFileKey(String key);
    @Query("""
        SELECT COUNT(l) > 0 FROM FinanceList l WHERE l.peladaId = :id AND l.qrId = :qrId
        AND l.status <> 'DRAFT' AND (:transparent = true OR EXISTS
        (SELECT c.id FROM FinanceCharge c WHERE c.listId = l.id AND c.userId = :userId))
        """)
    boolean visibleToMember(@Param("id") Long id, @Param("qrId") Long qrId,
            @Param("transparent") boolean transparent, @Param("userId") Long userId);
    Optional<FinanceQr> findByIdAndPeladaId(Long id, Long peladaId);
    List<FinanceQr> findByPeladaId(Long peladaId);
}
