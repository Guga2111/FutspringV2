package com.futspring.backend.domain.telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Against the real Tomcat, which decodes %73 before security and MVC match the route (MockMvc doesn't). Each test its
 * own client IP through X-Forwarded-For (the test client is a trusted proxy), so the 20/min rate limit never answers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClientErrorBodyLimitFilterTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anEncodedPathIsLimitedToo() throws Exception {
        assertThat(post("/api/v1/client-error%73", body(9000), "203.0.113.40")).isEqualTo(413);
    }

    @Test
    void aSmallReportOnTheEncodedPathStillArrives() throws Exception {
        assertThat(post("/api/v1/client-error%73", body(100), "203.0.113.41")).isEqualTo(204);
    }

    private static String body(int messagePadding) {
        return "{\"message\":\"boom" + "x".repeat(messagePadding) + "\",\"path\":\"/\"}";
    }

    private int post(String path, String body, String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
