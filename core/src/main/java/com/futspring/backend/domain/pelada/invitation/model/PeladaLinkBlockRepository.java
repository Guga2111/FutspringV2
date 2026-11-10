package com.futspring.backend.domain.pelada.invitation.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeladaLinkBlockRepository extends JpaRepository<PeladaLinkBlock, Long> {
    boolean existsByPeladaIdAndUserId(Long peladaId, Long userId);
    void deleteByPeladaIdAndUserId(Long peladaId, Long userId);
    void deleteByPeladaId(Long peladaId);

    @Modifying
    @Query("DELETE FROM PeladaLinkBlock b WHERE b.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
