package com.futspring.backend.domain.pelada;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PeladaLinkBlockRepository extends JpaRepository<PeladaLinkBlock, Long> {
    boolean existsByPeladaIdAndUserId(Long peladaId, Long userId);
    void deleteByPeladaIdAndUserId(Long peladaId, Long userId);
    void deleteByPeladaId(Long peladaId);
}
