package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.user.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Decides whether a JWT still opens a session: signed, not expired, the user still exists and the token
 * was issued with the user's current tokenVersion (a password reset increments it, revoking every older
 * token). Used by JwtAuthFilter (HTTP) and JwtChannelInterceptor (STOMP CONNECT). Costs one indexed
 * query per authenticated request.
 */
@Component
@RequiredArgsConstructor
public class AccessTokenVerifier {

    // Tokens issued before the version claim existed count as version 0, the default of User.tokenVersion
    private static final int VERSION_WITHOUT_CLAIM = 0;

    private final JwtService jwtService;
    private final UserRepository userRepository;

    // The caller's e-mail when the token opens a session, empty otherwise
    public Optional<String> verify(String token) {
        return jwtService.parseClaims(token)
                .filter(this::hasCurrentVersion)
                .map(Claims::getSubject);
    }

    // Empty when the user no longer exists, so a deleted account's token stops working too
    private boolean hasCurrentVersion(Claims claims) {
        return userRepository.findTokenVersionByEmail(claims.getSubject())
                .filter(current -> current == versionOf(claims))
                .isPresent();
    }

    private static int versionOf(Claims claims) {
        return Optional.ofNullable(claims.get(JwtService.VERSION_CLAIM, Integer.class)).orElse(VERSION_WITHOUT_CLAIM);
    }
}
