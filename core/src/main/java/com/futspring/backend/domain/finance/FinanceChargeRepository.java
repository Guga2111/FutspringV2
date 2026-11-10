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
    // People of `ids` who already have an active charge in the list
    @Query("SELECT c.personId FROM FinanceCharge c WHERE c.listId = :listId AND c.personId IN :ids AND c.canceled = false")
    List<Long> findActivePersonIds(@Param("listId") Long listId, @Param("ids") Collection<Long> ids);
    List<FinanceCharge> findByListIdIn(Collection<Long> listIds);
    List<FinanceCharge> findByIdInAndPeladaIdAndListIdOrderById(Collection<Long> ids, Long peladaId, Long listId);

    // Account export: the user's charges in lists that were published (drafts are the managers' work in progress)
    @EntityGraph(attributePaths = "components")
    @Query("""
        SELECT c FROM FinanceCharge c WHERE c.userId = :userId
        AND c.listId IN (SELECT l.id FROM FinanceList l WHERE l.publishedAt IS NOT NULL) ORDER BY c.id
        """)
    List<FinanceCharge> findPublishedByUserId(@Param("userId") Long userId);
}
