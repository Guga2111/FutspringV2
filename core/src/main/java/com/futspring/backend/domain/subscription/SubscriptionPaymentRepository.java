package com.futspring.backend.domain.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    // The payments outlive the pelada (the FK is ON DELETE SET NULL; done here too, as H2 has no FK actions)
    @Modifying
    @Query("UPDATE SubscriptionPayment p SET p.peladaId = null WHERE p.peladaId = :peladaId")
    void detachPelada(@Param("peladaId") Long peladaId);
}
