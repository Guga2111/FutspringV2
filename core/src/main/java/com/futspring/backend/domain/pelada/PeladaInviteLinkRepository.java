package com.futspring.backend.domain.pelada;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface PeladaInviteLinkRepository extends JpaRepository<PeladaInviteLink, Long> {
    Optional<PeladaInviteLink> findByPeladaIdAndRevokedAtIsNull(Long peladaId);
    @Query("select l.pelada.id from PeladaInviteLink l where l.id = :id")
    Optional<Long> findPeladaIdById(Long id);
    void deleteByPeladaId(Long peladaId);
}
