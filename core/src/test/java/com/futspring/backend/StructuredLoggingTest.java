package com.futspring.backend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

// The production log format, read from the main application.properties (the test one replaces it and keeps text):
// one ECS JSON line per event, the stack trace inside it, service.name for Loki. An empty format (dev) is plain text
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingTest.class);
    private static final String MARKER = "structured-logging-probe";

    @AfterEach
    void restoreTextLogging() {
        // back to the default text output, then marked uninitialized again (cleanUp), so the next context in this
        // JVM configures logging from its own properties instead of keeping this test's JSON
        LoggingSystem system = LoggingSystem.get(getClass().getClassLoader());
        system.cleanUp();
        system.beforeInitialize();
        system.initialize(new LoggingInitializationContext(new StandardEnvironment()), null, null);
        system.cleanUp();
    }

    @Test
    void logsOneEcsJsonLinePerEvent(CapturedOutput output) throws IOException {
        Map<String, Object> properties = mainLoggingProperties();
        assertThat(properties).containsEntry("logging.structured.format.console", "ecs");

        try (ConfigurableApplicationContext ignored = start(properties)) {
            log.error(MARKER, new IllegalStateException("boom"));
        }

        String line = output.getOut().lines().filter(l -> l.contains(MARKER)).findFirst().orElseThrow();
        JsonNode event = JsonMapper.builder().build().readTree(line);
        assertThat(event.path("@timestamp").asString()).isNotBlank();
        assertThat(event.path("message").asString()).isEqualTo(MARKER);
        assertThat(event.path("log").path("level").asString()).isEqualTo("ERROR");
        assertThat(event.path("log").path("logger").asString()).isEqualTo(StructuredLoggingTest.class.getName());
        assertThat(event.path("service").path("name").asString()).isEqualTo("futspring-api");
        assertThat(event.path("ecs").path("version").asString()).isNotBlank();
        assertThat(event.path("error").path("type").asString()).isEqualTo(IllegalStateException.class.getName());
        assertThat(event.path("error").path("stack_trace").asString()).contains("boom", "StructuredLoggingTest");
    }

    @Test
    void anEmptyFormatLogsPlainText(CapturedOutput output) throws IOException {
        Map<String, Object> properties = mainLoggingProperties();
        properties.put("logging.structured.format.console", "");

        try (ConfigurableApplicationContext ignored = start(properties)) {
            log.info(MARKER);
        }

        String line = output.getOut().lines().filter(l -> l.contains(MARKER)).findFirst().orElseThrow();
        assertThat(line).doesNotStartWith("{").contains("INFO");
    }

    private static ConfigurableApplicationContext start(Map<String, Object> properties) {
        return new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .run();
    }

    private static Map<String, Object> mainLoggingProperties() throws IOException {
        Properties main = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
            main.load(reader);
        }
        Map<String, Object> logging = new HashMap<>();
        main.stringPropertyNames().stream()
                .filter(name -> name.startsWith("logging."))
                .forEach(name -> logging.put(name, main.getProperty(name)));
        return logging;
    }

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }
}
