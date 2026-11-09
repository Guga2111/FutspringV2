package com.futspring.backend.domain.user.dto;

import com.futspring.backend.domain.user.DeletionBlockerType;

// Something the user must sort out in a pelada before the account can be deleted
public record DeletionBlockerDTO(
        DeletionBlockerType type,
        Long peladaId,
        String peladaName) {
}
