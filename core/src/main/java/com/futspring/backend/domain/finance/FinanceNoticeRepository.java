package com.futspring.backend.domain.finance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public interface FinanceNoticeRepository extends JpaRepository<FinanceNotice, Long> {
    boolean existsByReceiptKey(String key);
    @Query("""
        SELECT n FROM FinanceNotice n WHERE n.chargeId IN :ids AND
        (SELECT COUNT(newer.id) FROM FinanceNotice newer WHERE newer.chargeId = n.chargeId AND newer.id > n.id) < 10
        ORDER BY n.id DESC
        """)
    List<FinanceNotice> recentByChargeIds(@Param("ids") Collection<Long> ids);
    List<FinanceNotice> findByChargeIdInOrderByIdDesc(Collection<Long> ids);
    org.springframework.data.domain.Slice<FinanceNotice> findByPeladaIdAndChargeIdOrderByIdDesc(Long peladaId, Long chargeId, Pageable page);
    @Query("SELECT n FROM FinanceNotice n WHERE n.receiptExpiresAt <= :now AND n.receiptDeletedAt IS NULL AND n.id > :after ORDER BY n.id")
    List<FinanceNotice> expiredAfter(@Param("now") Instant now, @Param("after") Long after, Pageable page);
    Optional<FinanceNotice> findByIdAndPeladaId(Long id, Long peladaId);
    boolean existsByChargeIdAndStatus(Long chargeId, FinanceTypes.NoticeStatus status);
    List<FinanceNotice> findByReceiptExpiresAtLessThanEqualAndReceiptDeletedAtIsNull(Instant now, Pageable page);
}
