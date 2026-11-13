package com.futspring.backend.domain.auth;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against the real Tomcat (MockMvc neither decodes the URI like the container nor runs the RemoteIpValve). The JDK
 * HttpClient with URI.create sends the path as written, so %6C reaches the server encoded. The test client is
 * 127.0.0.1, a trusted proxy, so X-Forwarded-For gives each test its own client IP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthRateLimitFilterTest {

    private static final String LOGIN_BODY = "{\"email\":\"nobody@example.com\",\"password\":\"password123\"}";

    @LocalServerPort
    private int port;

    @Autowired
    private MeterRegistry meterRegistry;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void encodedLoginPathIsLimited() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 11; i++) statuses.add(login("/api/v1/auth/%6Cogin", "203.0.113.10"));

        assertThat(statuses.subList(0, 10)).containsOnly(401);
        assertThat(statuses.get(10)).isEqualTo(429);
    }

    @Test
    void plainAndEncodedPathsShareOneBucket() throws Exception {
        for (int i = 0; i < 5; i++) assertThat(login("/api/v1/auth/login", "203.0.113.11")).isEqualTo(401);
        for (int i = 0; i < 5; i++) assertThat(login("/api/v1/auth/%6Cogin", "203.0.113.11")).isEqualTo(401);

        assertThat(login("/api/v1/auth/login", "203.0.113.11")).isEqualTo(429);
        assertThat(meterRegistry.get("futspring.ratelimit.rejected").tag("route", "login").counter().count())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void injectedForwardedForDoesNotOpenNewBuckets() throws Exception {
        // what nginx's $proxy_add_x_forwarded_for produces: the client's value, then the real address
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            statuses.add(login("/api/v1/auth/login", "198.51.100." + (i + 1) + ", 203.0.113.12"));
        }

        assertThat(statuses.subList(0, 10)).containsOnly(401);
        assertThat(statuses.get(10)).isEqualTo(429);
    }

    @Test
    void unlimitedRoutesStayFree() throws Exception {
        for (int i = 0; i < 15; i++) {
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(uri("/api/v1/peladas/my"))
                    .header("X-Forwarded-For", "203.0.113.13").GET().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(401);
        }
    }

    private int login(String path, String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(LOGIN_BODY))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
