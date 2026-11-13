package com.futspring.backend.domain.auth;

import tools.jackson.databind.json.JsonMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Per-IP limits on the routes that check a password or a secret, can cause future sends, or read a lot: 10 requests
 * per minute on each POST /api/v1/auth/ route (login, register, forgot-password, reset-password), the logged-in
 * password checks (POST /api/v1/users/me/password and /me/delete), the Web Push subscription (POST
 * /api/v1/push/subscriptions), the public e-mail link routes (notifications unsubscribe / resubscribe) and the
 * account export (GET /api/v1/users/me/export); 30 per minute on the session refresh (POST /api/v1/auth/refresh);
 * 20 per minute on the web app's error reports
 * (POST /api/v1/client-errors); 60 per minute on the public invitation preview (GET /api/v1/invite-links/{token}).
 * Routes are matched with PathPatternRequestMatcher, on the decoded path (the mechanism Spring Security and MVC use),
 * never the raw request URI, so an encoded variant such as /auth/%6Cogin hits the same limit. The bucket key is the
 * route's logical name plus the client IP ("login:203.0.113.7"), never the URI received. The client IP is
 * getRemoteAddr(), which Tomcat's RemoteIpValve (server.forward-headers-strategy=native) takes from X-Forwarded-For
 * read right to left, skipping trusted proxies, so a value the client injects at the left is ignored.
 * Buckets live in a bounded, expiring Caffeine cache. In-memory: one API instance only. Every 429 counts in
 * futspring.ratelimit.rejected (tag route = the logical name); the cache is exposed as cache="futspring.ratelimit".
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final int REQUESTS_PER_MINUTE = 10;
    static final int REFRESHES_PER_MINUTE = 30;
    static final int CLIENT_ERRORS_PER_MINUTE = 20;
    static final int INVITE_PREVIEWS_PER_MINUTE = 60;

    private record Limit(String name, RequestMatcher matcher, int capacity) {}

    private static final List<Limit> LIMITS = List.of(
            limit("login", HttpMethod.POST, "/api/v1/auth/login", REQUESTS_PER_MINUTE),
            limit("register", HttpMethod.POST, "/api/v1/auth/register", REQUESTS_PER_MINUTE),
            limit("forgot-password", HttpMethod.POST, "/api/v1/auth/forgot-password", REQUESTS_PER_MINUTE),
            limit("reset-password", HttpMethod.POST, "/api/v1/auth/reset-password", REQUESTS_PER_MINUTE),
            limit("verification-email", HttpMethod.POST, "/api/v1/auth/verification-email", REQUESTS_PER_MINUTE),
            limit("verify-email", HttpMethod.POST, "/api/v1/auth/verify-email", REQUESTS_PER_MINUTE),
            limit("refresh", HttpMethod.POST, "/api/v1/auth/refresh", REFRESHES_PER_MINUTE),
            limit("change-password", HttpMethod.POST, "/api/v1/users/me/password", REQUESTS_PER_MINUTE),
            limit("delete-account", HttpMethod.POST, "/api/v1/users/me/delete", REQUESTS_PER_MINUTE),
            limit("push-subscribe", HttpMethod.POST, "/api/v1/push/subscriptions", REQUESTS_PER_MINUTE),
            limit("unsubscribe", HttpMethod.POST, "/api/v1/notifications/unsubscribe", REQUESTS_PER_MINUTE),
            limit("export", HttpMethod.GET, "/api/v1/users/me/export", REQUESTS_PER_MINUTE),
            limit("client-errors", HttpMethod.POST, "/api/v1/client-errors", CLIENT_ERRORS_PER_MINUTE),
            limit("invite-preview", HttpMethod.GET, "/api/v1/invite-links/{token}", INVITE_PREVIEWS_PER_MINUTE));

    private static Limit limit(String name, HttpMethod method, String pattern, int capacity) {
        return new Limit(name, PathPatternRequestMatcher.withDefaults().matcher(method, pattern), capacity);
    }

    private final JsonMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .recordStats()
            .build();

    public AuthRateLimitFilter(JsonMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        CaffeineCacheMetrics.monitor(meterRegistry, buckets, "futspring.ratelimit");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limitFor(request) == null;
    }

    private static Limit limitFor(HttpServletRequest request) {
        for (Limit limit : LIMITS) {
            if (limit.matcher().matches(request)) return limit;
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Limit limit = limitFor(request); // never null: shouldNotFilter skipped the other requests
        String route = limit.name();
        int capacity = limit.capacity();
        Bucket bucket = buckets.get(route + ":" + request.getRemoteAddr(), k -> Bucket.builder()
                .addLimit(l -> l.capacity(capacity).refillIntervally(capacity, Duration.ofMinutes(1)))
                .build());

        if (!bucket.tryConsume(1)) {
            meterRegistry.counter("futspring.ratelimit.rejected", "route", route).increment();
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
