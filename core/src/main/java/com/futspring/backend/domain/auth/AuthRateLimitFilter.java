package com.futspring.backend.domain.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 10 requests per minute per client IP on each POST /api/v1/auth/ route (login, register, forgot-password,
 * reset-password).
 * Buckets live in a bounded, expiring Caffeine cache. The client IP comes from getRemoteAddr(), which
 * reflects X-Forwarded-For because server.forward-headers-strategy=framework (the API only listens on
 * 127.0.0.1 behind nginx). In-memory: one API instance only.
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final int REQUESTS_PER_MINUTE = 10;
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/register",
            "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password");

    private final ObjectMapper objectMapper;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRequestURI() + "|" + request.getRemoteAddr();
        Bucket bucket = buckets.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(REQUESTS_PER_MINUTE,
                        Refill.intervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))))
                .build());

        if (!bucket.tryConsume(1)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                    "message", "Muitas tentativas. Tente novamente em instantes.",
                    "timestamp", Instant.now().toString()));
            return;
        }
        chain.doFilter(request, response);
    }
}
