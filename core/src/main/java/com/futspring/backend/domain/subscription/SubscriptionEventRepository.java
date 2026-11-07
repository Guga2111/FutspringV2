package com.futspring.backend.domain.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, Long> {

    @Modifying
    @Query("DELETE FROM SubscriptionEvent e WHERE e.peladaId = :peladaId")
    void deleteByPeladaId(@Param("peladaId") Long peladaId);
}
