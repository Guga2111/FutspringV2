package com.futspring.backend.domain.auth.session;

import com.futspring.backend.domain.auth.dto.AuthResponseDTO;

import java.time.Duration;

/**
 * What a login, a registration, a password change or a refresh hands the client: the access token in the body and the
 * refresh token for the cookie (SessionCookie). refreshToken is null when the session couldn't be stored (Redis down):
 * the response then carries no cookie and the access token alone keeps the client logged in until it expires.
 */
public record IssuedSession(AuthResponseDTO auth, String refreshToken, Duration refreshMaxAge) {
}
