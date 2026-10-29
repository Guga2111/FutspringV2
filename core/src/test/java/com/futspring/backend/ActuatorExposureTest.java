package com.futspring.backend;

import com.futspring.backend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Probes are public on the main port; every actuator endpoint lives only on the internal management port
@AutoConfigureObservability
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
    void health_showsDetailsOnTheManagementPort() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + managementPort + "/actuator/health/readiness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
