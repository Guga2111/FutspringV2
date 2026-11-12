package com.futspring.backend.domain.auth.session;

import com.futspring.backend.domain.auth.JwtService;
import com.futspring.backend.domain.auth.SecretTokens;
import com.futspring.backend.domain.auth.dto.AuthResponseDTO;
import com.futspring.backend.domain.auth.dto.UserResponseDTO;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.config.CorsConfig;
import com.futspring.backend.shared.exception.AppException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh-token sessions, stored in Redis. A session (sess:{id}) lives 30 days after its last use, never more than 90
 * after it was opened; each refresh token (rt:{sha256}) points at its session and carries the user's tokenVersion, so a
 * password reset, a password change or an account deletion invalidates every session without an index per user.
 * A refresh hands out a new refresh token; the previous one stays valid until its own TTL (no reuse detection: two tabs
 * or the PWA and the browser refreshing together must not log anyone out). Logout deletes the session, so every
 * refresh token of it stops working. Redis down: login still answers an access token (no cookie), refresh answers 503.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    static final Duration IDLE_TTL = Duration.ofDays(30);
    static final Duration ABSOLUTE_TTL = Duration.ofDays(90);
    static final String TOKEN_KEY = "rt:";
    static final String SESSION_KEY = "sess:";

    record StoredToken(long userId, String sessionId, int tokenVersion) {
    }

    record StoredSession(long userId, Instant createdAt, Instant absoluteExpiresAt, Instant lastUsedAt) {
    }

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CorsConfig corsConfig;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    /** A new session for a user who just proved who they are (login, registration, password change). */
    public IssuedSession open(User user) {
        AuthResponseDTO auth = accessFor(user);
        Instant now = clock.instant();
        StoredSession session = new StoredSession(user.getId(), now, now.plus(ABSOLUTE_TTL), now);
        try {
            IssuedSession issued = issue(auth, UUID.randomUUID().toString(), session, user.getTokenVersion(), now);
            count("futspring.session.open", "ok");
            return issued;
        } catch (DataAccessException e) {
            count("futspring.session.open", "error");
            log.error("Refresh session not stored for user {}: {}", user.getId(), e.getMessage());
            return new IssuedSession(auth, null, null);
        }
    }

    /**
     * A new access token and refresh token for the session of this refresh token; empty when it no longer opens one
     * (unknown, expired, logged out, revoked by tokenVersion, deleted account). The browser must send an Origin of
     * ALLOWED_ORIGINS (403 otherwise); Redis down → 503, so the client can tell an outage from an ended session.
     */
    public Optional<IssuedSession> refresh(String refreshToken, String origin) {
        if (origin == null || !corsConfig.getAllowedOrigins().contains(origin)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Origem não permitida");
        }
        try {
            Optional<IssuedSession> issued = rotate(refreshToken);
            count("futspring.session.refresh", issued.isPresent() ? "ok" : "invalid");
            return issued;
        } catch (DataAccessException e) {
            count("futspring.session.refresh", "error");
            log.error("Refresh failed: {}", e.getMessage());
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível renovar a sessão agora. Tente de novo em instantes.");
        }
    }

    /**
     * Logout: the session of this refresh token stops refreshing (the user's other sessions stay). Idempotent. With
     * Redis down the session is left to expire; the cookie is cleared anyway, so this browser can't use it.
     */
    public void close(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        try {
            String tokenKey = TOKEN_KEY + SecretTokens.hash(refreshToken);
            StoredToken stored = read(tokenKey, StoredToken.class);
            if (stored != null) redis.delete(List.of(SESSION_KEY + stored.sessionId(), tokenKey));
        } catch (DataAccessException e) {
            log.warn("Refresh session not closed: {}", e.getMessage());
        }
    }

    private Optional<IssuedSession> rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return Optional.empty();
        StoredToken stored = read(TOKEN_KEY + SecretTokens.hash(refreshToken), StoredToken.class);
        if (stored == null) return Optional.empty();
        StoredSession session = read(SESSION_KEY + stored.sessionId(), StoredSession.class);
        Instant now = clock.instant();
        // Redis expires keys on its own clock; the 90-day ceiling is also checked here, on the application's
        if (session == null || session.userId() != stored.userId() || !now.isBefore(session.absoluteExpiresAt())) {
            return Optional.empty();
        }
        Optional<User> user = userRepository.findById(stored.userId())
                .filter(u -> !u.isDeleted() && u.getTokenVersion() == stored.tokenVersion());
        if (user.isEmpty()) return Optional.empty();

        StoredSession used = new StoredSession(session.userId(), session.createdAt(), session.absoluteExpiresAt(), now);
        return Optional.of(issue(accessFor(user.get()), stored.sessionId(), used, stored.tokenVersion(), now));
    }

    // Writes the session (TTL renewed) and a new refresh token pointing at it, both until min(30 days, the ceiling)
    private IssuedSession issue(AuthResponseDTO auth, String sessionId, StoredSession session, int tokenVersion,
                                Instant now) {
        Duration untilCeiling = Duration.between(now, session.absoluteExpiresAt());
        Duration ttl = untilCeiling.compareTo(IDLE_TTL) < 0 ? untilCeiling : IDLE_TTL;
        String refreshToken = SecretTokens.generate();
        write(SESSION_KEY + sessionId, session, ttl);
        write(TOKEN_KEY + SecretTokens.hash(refreshToken), new StoredToken(session.userId(), sessionId, tokenVersion), ttl);
        return new IssuedSession(auth, refreshToken, ttl);
    }

    private AuthResponseDTO accessFor(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getTokenVersion());
        return new AuthResponseDTO(token, UserResponseDTO.from(user));
    }

    private <T> T read(String key, Class<T> type) {
        String value = redis.opsForValue().get(key);
        return value == null ? null : jsonMapper.readValue(value, type);
    }

    private void write(String key, Object value, Duration ttl) {
        redis.opsForValue().set(key, jsonMapper.writeValueAsString(value), ttl);
    }

    private void count(String metric, String result) {
        meterRegistry.counter(metric, "result", result).increment();
    }
}
