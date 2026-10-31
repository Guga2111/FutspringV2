package com.futspring.backend.domain.pelada.dto;

import com.futspring.backend.domain.pelada.*;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import java.time.Instant;

public record InvitationDTO(Long id, Long peladaId, PeladaPreviewDTO pelada, PublicUserDTO recipient,
        PublicUserDTO sender, InvitationStatus status, Instant createdAt, Instant expiresAt,
        Instant respondedAt, Instant canceledAt, boolean isMember) {
    public static InvitationDTO from(PeladaInvitation i, Instant now, boolean member) {
        return new InvitationDTO(i.getId(), i.getPelada().getId(), PeladaPreviewDTO.from(i.getPelada()),
                PublicUserDTO.from(i.getRecipient()), PublicUserDTO.from(i.getSender()), i.statusAt(now),
                i.getCreatedAt(), i.getExpiresAt(), i.getRespondedAt(), i.getCanceledAt(), member);
    }
}
