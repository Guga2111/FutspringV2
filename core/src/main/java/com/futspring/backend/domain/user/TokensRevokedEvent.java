package com.futspring.backend.domain.user;

/**
 * The user's tokenVersion went up (password reset, password change, account deletion): every session opened before
 * is revoked. Carries the e-mail the sessions were opened with (the deletion changes it right after).
 */
public record TokensRevokedEvent(String email) {
}
