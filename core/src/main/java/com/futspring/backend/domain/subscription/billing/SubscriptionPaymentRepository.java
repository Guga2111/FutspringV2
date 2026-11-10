package com.futspring.backend.domain.subscription.billing;

import com.futspring.backend.domain.subscription.PeladaSubscription;
import com.futspring.backend.domain.subscription.SubscriptionKind;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    // Two voids of the same payment can't both pass the "already voided" check
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM SubscriptionPayment p WHERE p.id = :id")
    Optional<SubscriptionPayment> findByIdForUpdate(@Param("id") Long id);

    // Voided payments included (shown struck through), newest first
    @EntityGraph(attributePaths = "recordedBy")
    Page<SubscriptionPayment> findByPaidOnBetweenOrderByPaidOnDescIdDesc(LocalDate from, LocalDate to, Pageable pageable);

    @EntityGraph(attributePaths = "recordedBy")
    List<SubscriptionPayment> findByPeladaIdOrderByPaidOnDescIdDesc(Long peladaId, Pageable pageable);

    // What the creator sees: voided payments are the platform admin's business
    List<SubscriptionPayment> findByPeladaIdAndVoidedAtIsNullOrderByPaidOnDescIdDesc(Long peladaId, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0) FROM SubscriptionPayment p
        WHERE p.voidedAt IS NULL AND p.paidOn BETWEEN :from AND :to
        """)
    BigDecimal sumBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // [year, month, total] of the payments not voided, paid from `from` on
    @Query("""
        SELECT YEAR(p.paidOn), MONTH(p.paidOn), SUM(p.amount) FROM SubscriptionPayment p
        WHERE p.voidedAt IS NULL AND p.paidOn >= :from
        GROUP BY YEAR(p.paidOn), MONTH(p.paidOn)
        """)
    List<Object[]> sumByMonthSince(@Param("from") LocalDate from);

    // [plan, total] of the payments not voided whose period covers `now`, of peladas still PAID
    @Query("""
        SELECT p.plan, SUM(p.amount) FROM SubscriptionPayment p
        WHERE p.voidedAt IS NULL AND p.periodStart <= :now AND p.periodEnd > :now
          AND p.peladaId IN (SELECT s.pelada.id FROM PeladaSubscription s
                             WHERE s.kind = com.futspring.backend.domain.subscription.SubscriptionKind.PAID)
        GROUP BY p.plan
        """)
    List<Object[]> sumCoveringByPlan(@Param("now") Instant now);

    // The payments outlive the pelada (the FK is ON DELETE SET NULL; done here too, as H2 has no FK actions)
    @Modifying
    @Query("UPDATE SubscriptionPayment p SET p.peladaId = null WHERE p.peladaId = :peladaId")
    void detachPelada(@Param("peladaId") Long peladaId);

    // Account export: the payments the user made (voided ones included), newest first
    List<SubscriptionPayment> findByPayer_IdOrderByPaidOnDescIdDesc(Long payerId);
}
