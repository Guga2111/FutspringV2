package com.futspring.backend.domain.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PeladaSubscriptionRepository extends JpaRepository<PeladaSubscription, Long> {

    Optional<PeladaSubscription> findByPeladaId(Long peladaId);

    @Query("SELECT s FROM PeladaSubscription s WHERE s.pelada.id IN :peladaIds")
    List<PeladaSubscription> findByPeladaIdIn(@Param("peladaIds") Collection<Long> peladaIds);

    @Modifying
    @Query("DELETE FROM PeladaSubscription s WHERE s.pelada.id = :peladaId")
    void deleteByPeladaId(@Param("peladaId") Long peladaId);
}
