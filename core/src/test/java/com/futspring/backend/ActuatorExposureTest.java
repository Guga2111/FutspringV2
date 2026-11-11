package com.futspring.backend;

import com.futspring.backend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Probes are public on the main port; every actuator endpoint lives only on the internal management port
@AutoConfigureMetrics
@AutoConfigureTestRestTemplate
class ActuatorExposureTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void probes_arePublicOnTheMainPort() throws Exception {
        mockMvc.perform(get("/livez")).andExpect(status().isOk());
        mockMvc.perform(get("/readyz")).andExpect(status().isOk());
    }

    @Test
    void actuatorEndpoints_areNotServedOnTheMainPort() {
        assertThat(managementPort).isNotEqualTo(serverPort);
        for (String path : new String[] {"/actuator/health", "/actuator/prometheus", "/actuator/threaddump"}) {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "http://localhost:" + serverPort + path, String.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).as(path).isFalse();
        }
    }

    @Test
    void prometheus_isServedOnTheManagementPortWithoutAToken() {
        restTemplate.getForEntity("http://localhost:" + serverPort + "/readyz", String.class);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + managementPort + "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("http_server_requests_seconds")
                .contains("application=\"futspring\"")
                .contains("futspring_email_queue_size")
                .contains("cache=\"futspring.ratelimit\"");
    }

    @Test
    void managementPort_exposesOnlyHealthAndPrometheus() {
        for (String path : new String[] {"/actuator/threaddump", "/actuator/metrics", "/actuator/info",
                "/actuator/env", "/actuator/heapdump"}) {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "http://localhost:" + managementPort + path, String.class);
            // not exposed → not an actuator endpoint, so the API chain answers (401 without a token)
            assertThat(response.getStatusCode().is2xxSuccessful()).as(path).isFalse();
        }
    }

    @Test
    void health_hidesDetailsOnTheManagementPort() {
        for (String path : new String[] {"/actuator/health", "/actuator/health/readiness"}) {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "http://localhost:" + managementPort + path, String.class);

            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as(path)
                    .contains("\"status\":\"UP\"")
                    .doesNotContain("components")
                    .doesNotContain("details");
        }
    }
}
