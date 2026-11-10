package com.futspring.backend.domain.pelada.invitation.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PeladaInviteLinkRepository extends JpaRepository<PeladaInviteLink, Long> {
    Optional<PeladaInviteLink> findByPeladaIdAndRevokedAtIsNull(Long peladaId);
    @Query("select l.pelada.id from PeladaInviteLink l where l.id = :id")
    Optional<Long> findPeladaIdById(Long id);
    void deleteByPeladaId(Long peladaId);

    @Modifying
    @Query("UPDATE PeladaInviteLink l SET l.revokedAt = :now WHERE l.creator.id = :userId AND l.revokedAt IS NULL")
    void revokeCreatedBy(@Param("userId") Long userId, @Param("now") Instant now);
}
