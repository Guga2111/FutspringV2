package com.futspring.backend.domain.auth.session;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Redis down (the test configuration points at a closed port): logging in still works, without a refresh cookie,
// refresh answers 503 (an outage, not an ended session: the cookie stays) and logout still clears the cookie
class RefreshWithoutRedisTest extends BaseIntegrationTest {

    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MeterRegistry meterRegistry;

    @Test
    void loginAnswersAnAccessTokenWithoutACookie() throws Exception {
        users.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH).username("semredis").email("semredis@example.com")
                .password(passwordEncoder.encode("senha-123")).build());
        double errors = count("futspring.session.open");

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"semredis@example.com\",\"password\":\"senha-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(count("futspring.session.open")).isEqualTo(errors + 1);
    }

    @Test
    void refreshIs503AndKeepsTheCookie() throws Exception {
        double errors = count("futspring.session.refresh");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .cookie(new Cookie(SessionCookie.NAME, "some-token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(count("futspring.session.refresh")).isEqualTo(errors + 1);
    }

    @Test
    void logoutStillClearsTheCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(SessionCookie.NAME, "some-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    private double count(String metric) {
        var counter = meterRegistry.find(metric).tag("result", "error").counter();
        return counter == null ? 0 : counter.count();
    }
}
