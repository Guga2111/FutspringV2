package com.futspring.backend.domain.subscription;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, Long> {

    // A pelada's history, newest first, with the actor (null = the system)
    @EntityGraph(attributePaths = "actor")
    List<SubscriptionEvent> findByPeladaIdOrderByCreatedAtDescIdDesc(Long peladaId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM SubscriptionEvent e WHERE e.peladaId = :peladaId")
    void deleteByPeladaId(@Param("peladaId") Long peladaId);
}
