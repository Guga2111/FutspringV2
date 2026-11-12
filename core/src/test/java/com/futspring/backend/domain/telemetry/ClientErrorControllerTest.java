package com.futspring.backend.domain.telemetry;

import com.futspring.backend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class ClientErrorControllerTest extends BaseIntegrationTest {

    private static final AtomicInteger IPS = new AtomicInteger();

    private static Map<String, Object> report() {
        Map<String, Object> body = new HashMap<>();
        body.put("message", "TypeError: x is undefined");
        body.put("stack", "TypeError: x is undefined\n    at https://futspring.test/assets/index-abc.js:1:200");
        body.put("path", "/pelada/1");
        body.put("release", "abc1234");
        body.put("userAgent", "Mozilla/5.0");
        body.put("source", "WINDOW");
        return body;
    }

    private ResultActions send(Object body, RequestPostProcessor ip) throws Exception {
        return mockMvc.perform(post("/api/v1/client-errors").with(ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    // each test its own IP, so the rate limit buckets of the other tests never interfere
    private static RequestPostProcessor nextIp() {
        int n = IPS.incrementAndGet();
        return r -> {
            r.setRemoteAddr("10.88.0." + n);
            return r;
        };
    }

    @Test
    void logsTheReportWithoutAToken(CapturedOutput output) throws Exception {
        Map<String, Object> body = report();
        body.put("path", "/reset-password?token=secret#x");

        send(body, nextIp()).andExpect(status().isNoContent());

        String line = output.getOut().lines().filter(l -> l.contains("TypeError: x is undefined")).findFirst()
                .orElseThrow();
        assertThat(line).contains("WARN", "Erro no cliente em /reset-password:").doesNotContain("secret");
    }

    @Test
    void ignoresAnInvalidOrExpiredToken() throws Exception {
        mockMvc.perform(post("/api/v1/client-errors").with(nextIp())
                        .header("Authorization", "Bearer not-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(report())))
                .andExpect(status().isNoContent());
    }

    @Test
    void acceptsAReportWithOnlyMessageAndPath() throws Exception {
        send(Map.of("message", "boom", "path", "/"), nextIp()).andExpect(status().isNoContent());
    }

    @Test
    void validatesTheFields() throws Exception {
        Map<String, Object> blank = report();
        blank.put("message", " ");
        send(blank, nextIp()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.message").exists());

        Map<String, Object> longMessage = report();
        longMessage.put("message", "a".repeat(501));
        send(longMessage, nextIp()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists());

        Map<String, Object> longStack = report();
        longStack.put("stack", "a".repeat(4001));
        send(longStack, nextIp()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.stack").exists());

        Map<String, Object> noPath = report();
        noPath.remove("path");
        send(noPath, nextIp()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.path").exists());

        Map<String, Object> unknownSource = report();
        unknownSource.put("source", "SERVER");
        send(unknownSource, nextIp()).andExpect(status().isBadRequest());
    }

    @Test
    void refusesABodyOver8Kb() throws Exception {
        Map<String, Object> body = report();
        body.put("padding", "a".repeat(ClientErrorBodyLimitFilter.MAX_BODY_BYTES));
        send(body, nextIp()).andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void isRateLimitedPerIp() throws Exception {
        RequestPostProcessor sameIp = nextIp();
        for (int i = 0; i < 20; i++) {
            send(report(), sameIp).andExpect(status().isNoContent());
        }
        send(report(), sameIp).andExpect(status().isTooManyRequests());
    }
}
