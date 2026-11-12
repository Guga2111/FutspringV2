package com.futspring.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The API talks to a password-protected Redis like the production one (same image and flags), and Redis never
// takes the API out of health or readiness: an outage only stops refreshing sessions. Skipped without Docker.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
class RedisConnectionTest {

    private static final String PASSWORD = "test-redis-password";

    // same image and flags as docker-compose.prod.yml
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.12-alpine")
            .withCommand("redis-server", "--appendonly", "yes", "--requirepass", PASSWORD)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> PASSWORD);
    }

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalManagementPort
    private int managementPort;

    @Test
    void readsAndWritesWithThePassword_andStaysReadyWhenRedisStops() {
        redis.opsForValue().set("redis-connection-test", "ok", Duration.ofMinutes(1));
        assertThat(redis.opsForValue().get("redis-connection-test")).isEqualTo("ok");

        REDIS.stop();

        assertThatThrownBy(() -> redis.opsForValue().get("redis-connection-test"))
                .isInstanceOf(RuntimeException.class);
        for (String path : new String[] {"/actuator/health", "/actuator/health/readiness"}) {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "http://localhost:" + managementPort + path, String.class);

            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as(path).contains("\"status\":\"UP\"");
        }
    }
}
