package com.futspring.backend.domain.finance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinanceListRepository extends JpaRepository<FinanceList, Long> {
    List<FinanceList> findByPeladaId(Long id);
    @Query("""
        SELECT l FROM FinanceList l WHERE l.peladaId = :id AND
        (:manage = true OR (l.status <> 'DRAFT' AND
        (:transparent = true OR EXISTS (SELECT c.id FROM FinanceCharge c WHERE c.listId = l.id AND c.userId = :userId))))
        ORDER BY l.id DESC
        """)
    Slice<FinanceList> visible(@Param("id") Long id, @Param("manage") boolean manage,
            @Param("transparent") boolean transparent, @Param("userId") Long userId, Pageable page);
    List<FinanceList> findByPeladaIdOrderByIdDesc(Long peladaId, Pageable page);
    Optional<FinanceList> findByIdAndPeladaId(Long id, Long peladaId);
    boolean existsByPeladaIdAndPublishedAtIsNotNull(Long peladaId);
    List<FinanceList> findByDailyId(Long dailyId);
}
