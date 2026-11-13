package com.futspring.backend.domain.auth.session;

import com.futspring.backend.domain.auth.LegalTerms;
import com.futspring.backend.domain.auth.SecretTokens;
import com.futspring.backend.domain.auth.passwordreset.PasswordResetToken;
import com.futspring.backend.domain.auth.passwordreset.PasswordResetTokenRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

// Refresh-token sessions against a real Redis (the production image, with a password); skipped without Docker.
// The no-Redis behavior (login without a cookie, refresh 503) is in RefreshWithoutRedisTest.
@Testcontainers(disabledWithoutDocker = true)
class RefreshSessionTest extends BaseIntegrationTest {

    private static final String REDIS_PASSWORD = "test-redis-password";
    private static final String PASSWORD = "senha-atual-123";
    private static final String ORIGIN = "http://localhost:5173";
    // Every request gets its own client IP, so the rate limits only bite where tested
    private static final AtomicInteger IP = new AtomicInteger();

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.12-alpine")
            .withCommand("redis-server", "--appendonly", "yes", "--requirepass", REDIS_PASSWORD)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
    }

    @MockitoBean Clock clock;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PasswordResetTokenRepository resetTokens;
    @Autowired StringRedisTemplate redis;
    @Autowired MeterRegistry meterRegistry;

    Instant now;
    User user;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        when(clock.instant()).thenAnswer(invocation -> now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        user = users.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH)
                .username("sessao" + IP.incrementAndGet())
                .email("sessao" + IP.get() + "@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .build());
    }

    // --- cookie ---

    @Test
    void login_setsTheRefreshCookieAndStoresTheSession() throws Exception {
        MockHttpServletResponse response = login();

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(SessionCookie.NAME + "=")
                .contains("Path=/api/v1/auth", "Max-Age=2592000", "Secure", "HttpOnly", "SameSite=Strict");
        String refreshToken = valueOf(setCookie);
        String tokenKey = RefreshSessionService.TOKEN_KEY + SecretTokens.hash(refreshToken);
        assertThat(redis.hasKey(tokenKey)).isTrue();
        assertThat(redis.getExpire(tokenKey, TimeUnit.DAYS)).isBetween(29L, 30L);
        assertThat(redis.opsForValue().get(tokenKey)).doesNotContain(refreshToken);
    }

    @Test
    void register_doesNotIssueASessionBeforeVerification() throws Exception {
        mockMvc.perform(fromNewIp(post("/api/v1/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"New User\",\"email\":\"new@example.com\",\"password\":\"password123\",\"termsVersion\":\"" + com.futspring.backend.domain.auth.LegalTerms.CURRENT_VERSION + "\"}"))
                .andExpect(status().isNoContent()).andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void refresh_rotatesTheCookieAndAnswersAWorkingAccessToken() throws Exception {
        String first = valueOf(login().getHeader(HttpHeaders.SET_COOKIE));

        MockHttpServletResponse response = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andReturn().getResponse();

        String second = valueOf(response.getHeader(HttpHeaders.SET_COOKIE));
        assertThat(second).isNotEqualTo(first);
        String accessToken = objectMapper.readTree(response.getContentAsString()).get("token").asText();
        mockMvc.perform(get("/api/v1/users/" + user.getId()).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        // no reuse detection: the previous refresh token keeps working for a request already in flight (another tab)
        refresh(first).andExpect(status().isOk());
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void refresh_leavesTheReplacedTokenOnlyAMinute_soKeysDoNotPileUp() throws Exception {
        String token = valueOf(login().getHeader(HttpHeaders.SET_COOKIE));
        String first = RefreshSessionService.TOKEN_KEY + SecretTokens.hash(token);

        for (int i = 0; i < 5; i++) {
            token = valueOf(refresh(token).andExpect(status().isOk())
                    .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE));
        }

        assertThat(redis.getExpire(first, TimeUnit.SECONDS)).isBetween(1L, 60L);
        // the current token keeps the session's full TTL
        assertThat(redis.getExpire(RefreshSessionService.TOKEN_KEY + SecretTokens.hash(token), TimeUnit.DAYS))
                .isBetween(29L, 30L);
    }

    @Test
    void refresh_ignoresAnExpiredAuthorizationHeader() throws Exception {
        String refreshToken = valueOf(login().getHeader(HttpHeaders.SET_COOKIE));

        mockMvc.perform(fromNewIp(post("/api/v1/auth/refresh"))
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header("Authorization", "Bearer not-a-valid-token")
                        .cookie(new Cookie(SessionCookie.NAME, refreshToken)))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_withoutOrUnknownCookie_is401AndClearsTheCookie() throws Exception {
        String cleared = mockMvc.perform(fromNewIp(post("/api/v1/auth/refresh")).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sessão expirada. Entre novamente."))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cleared).startsWith(SessionCookie.NAME + "=;").contains("Max-Age=0", "Path=/api/v1/auth");

        refresh("made-up").andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_needsAnAllowedOrigin() throws Exception {
        String refreshToken = valueOf(login().getHeader(HttpHeaders.SET_COOKIE));

        mockMvc.perform(fromNewIp(post("/api/v1/auth/refresh")).cookie(new Cookie(SessionCookie.NAME, refreshToken)))
                .andExpect(status().isForbidden());
        // same origin as the request (passes CORS) but not in ALLOWED_ORIGINS
        MockHttpServletResponse response = mockMvc.perform(fromNewIp(post("/api/v1/auth/refresh"))
                        .header(HttpHeaders.ORIGIN, "http://localhost")
                        .cookie(new Cookie(SessionCookie.NAME, refreshToken)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        mockMvc.perform(fromNewIp(post("/api/v1/auth/refresh"))
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(new Cookie(SessionCookie.NAME, refreshToken)))
                .andExpect(status().isForbidden());

        refresh(refreshToken).andExpect(status().isOk());
    }

    @Test
    void refresh_isRateLimitedPerIp() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(post("/api/v1/auth/refresh").with(r -> { r.setRemoteAddr("10.9.9.9"); return r; })
                            .header(HttpHeaders.ORIGIN, ORIGIN))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/refresh").with(r -> { r.setRemoteAddr("10.9.9.9"); return r; })
                        .header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void refresh_countsItsResults() throws Exception {
        double ok = count("futspring.session.refresh", "ok");
        double invalid = count("futspring.session.refresh", "invalid");

        refresh(valueOf(login().getHeader(HttpHeaders.SET_COOKIE))).andExpect(status().isOk());
        refresh("made-up").andExpect(status().isUnauthorized());

        assertThat(count("futspring.session.refresh", "ok")).isEqualTo(ok + 1);
        assertThat(count("futspring.session.refresh", "invalid")).isEqualTo(invalid + 1);
    }

    // --- 90-day ceiling ---

    @Test
    void session_neverOutlivesNinetyDays() throws Exception {
        Instant openedAt = now;
        String refreshToken = valueOf(login().getHeader(HttpHeaders.SET_COOKIE));

        // used every few weeks it keeps sliding, but the last refresh before the ceiling only lasts until it
        for (int day = 25; day <= 75; day += 25) {
            now = openedAt.plus(Duration.ofDays(day));
            refreshToken = valueOf(refresh(refreshToken).andExpect(status().isOk())
                    .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE));
        }
        now = openedAt.plus(Duration.ofDays(89));
        String last = refresh(refreshToken).andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(last).contains("Max-Age=86400");
        assertThat(redis.getExpire(RefreshSessionService.TOKEN_KEY + SecretTokens.hash(valueOf(last)), TimeUnit.SECONDS))
                .isBetween(86_000L, 86_400L);

        now = openedAt.plus(Duration.ofDays(90));
        refresh(valueOf(last)).andExpect(status().isUnauthorized());
    }

    // --- ending a session ---

    @Test
    void logout_endsOnlyThisSession_andIsIdempotent() throws Exception {
        String thisDevice = valueOf(login().getHeader(HttpHeaders.SET_COOKIE));
        String otherDevice = valueOf(login().getHeader(HttpHeaders.SET_COOKIE));
        String rotated = valueOf(refresh(thisDevice).andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE));

        for (int i = 0; i < 2; i++) {
            String cleared = mockMvc.perform(fromNewIp(post("/api/v1/auth/logout"))
                            .cookie(new Cookie(SessionCookie.NAME, rotated)))
                    .andExpect(status().isNoContent())
                    .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(cleared).contains("Max-Age=0");
        }
        mockMvc.perform(fromNewIp(post("/api/v1/auth/logout"))).andExpect(status().isNoContent());

        // every refresh token of the session stops, the earlier one included; the other device stays
        refresh(rotated).andExpect(status().isUnauthorized());
        refresh(thisDevice).andExpect(status().isUnauthorized());
        refresh(otherDevice).andExpect(status().isOk());
    }

    @Test
    void passwordReset_endsEverySession() throws Exception {
        String refreshToken = valueOf(login().getHeader(HttpHeaders.SET_COOKIE));
        resetTokens.save(PasswordResetToken.builder()
                .user(user).tokenHash(SecretTokens.hash("reset-token"))
                .expiresAt(LocalDateTime.now().plusMinutes(30)).build());

        mockMvc.perform(fromNewIp(post("/api/v1/auth/reset-password"))
                        .content(json(Map.of("token", "reset-token", "newPassword", "nova-senha-123"))))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChange_endsTheOtherSessions_andOpensANewOne() throws Exception {
        String before = valueOf(login().getHeader(HttpHeaders.SET_COOKIE));

        String setCookie = mockMvc.perform(fromNewIp(post("/api/v1/users/me/password"))
                        .header("Authorization", bearerToken(user.getId(), user.getEmail()))
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "nova-senha-123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("Path=/api/v1/auth", "HttpOnly");
        refresh(before).andExpect(status().isUnauthorized());
        refresh(valueOf(setCookie)).andExpect(status().isOk());
    }

    @Test
    void accountDeletion_endsEverySession() throws Exception {
        String refreshToken = valueOf(login().getHeader(HttpHeaders.SET_COOKIE));

        mockMvc.perform(fromNewIp(post("/api/v1/users/me/delete"))
                        .header("Authorization", bearerToken(user.getId(), user.getEmail()))
                        .content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private MockHttpServletResponse login() throws Exception {
        return mockMvc.perform(fromNewIp(post("/api/v1/auth/login"))
                        .content(json(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(fromNewIp(post("/api/v1/auth/refresh"))
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .cookie(new Cookie(SessionCookie.NAME, refreshToken)));
    }

    private MockHttpServletRequestBuilder fromNewIp(MockHttpServletRequestBuilder request) {
        int n = IP.incrementAndGet();
        String ip = "10.2." + (n / 250) + "." + (n % 250 + 1);
        return request.with(r -> { r.setRemoteAddr(ip); return r; }).contentType(MediaType.APPLICATION_JSON);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private double count(String metric, String result) {
        var counter = meterRegistry.find(metric).tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    // The cookie's value in a Set-Cookie header
    private static String valueOf(String setCookie) {
        assertThat(setCookie).as("Set-Cookie").startsWith(SessionCookie.NAME + "=");
        return setCookie.substring(SessionCookie.NAME.length() + 1, setCookie.indexOf(';'));
    }
}
