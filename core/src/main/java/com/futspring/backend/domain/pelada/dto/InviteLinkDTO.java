package com.futspring.backend.domain.pelada.dto;

import com.futspring.backend.domain.user.dto.PublicUserDTO;
import java.time.Instant;

public record InviteLinkDTO(String token, Instant createdAt, Instant expiresAt, PublicUserDTO creator) { }
