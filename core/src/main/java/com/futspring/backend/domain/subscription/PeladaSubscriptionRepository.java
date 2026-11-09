package com.futspring.backend.domain.subscription;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PeladaSubscriptionRepository extends JpaRepository<PeladaSubscription, Long>,
        JpaSpecificationExecutor<PeladaSubscription> {

    Optional<PeladaSubscription> findByPeladaId(Long peladaId);

    @Query("SELECT s FROM PeladaSubscription s WHERE s.pelada.id IN :peladaIds")
    List<PeladaSubscription> findByPeladaIdIn(@Param("peladaIds") Collection<Long> peladaIds);

    // The platform admin's pelada list: the page comes with each pelada and its creator (the count query has no join)
    @Override
    @EntityGraph(attributePaths = {"pelada", "pelada.creator"})
    Page<PeladaSubscription> findAll(Specification<PeladaSubscription> spec, Pageable pageable);

    // Trials ending before `until` (still running), soonest first
    @Query("""
        SELECT s FROM PeladaSubscription s JOIN FETCH s.pelada
        WHERE s.kind = com.futspring.backend.domain.subscription.SubscriptionKind.TRIAL
          AND s.validUntil > :now AND s.validUntil <= :until
        ORDER BY s.validUntil, s.id
        """)
    List<PeladaSubscription> findTrialsEndingBetween(@Param("now") Instant now, @Param("until") Instant until,
                                                     Pageable pageable);

    // Creators who said they paid by Pix and wait for the platform admin, oldest claim first
    @Query("""
        SELECT s FROM PeladaSubscription s JOIN FETCH s.pelada
        WHERE s.pixClaimedAt IS NOT NULL
        ORDER BY s.pixClaimedAt, s.id
        """)
    List<PeladaSubscription> findPixClaimed(Pageable pageable);

    @Modifying
    @Query("DELETE FROM PeladaSubscription s WHERE s.pelada.id = :peladaId")
    void deleteByPeladaId(@Param("peladaId") Long peladaId);
}
