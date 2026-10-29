package com.futspring.backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// Runs every migration on an empty Postgres and lets Hibernate validate the result against the entities.
// The context fails to start if a migration breaks or the schema drifts from an entity.
// Skipped when Docker isn't available.
@SpringBootTest
@ActiveProfiles("prod") // no DataInitializer seed
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    // same major version as production (Supabase)
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsApplyOnEmptyDatabaseAndMatchEntities() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(Arrays.stream(applied).map(m -> m.getVersion().getVersion()))
                .startsWith("1");
        assertThat(flyway.info().pending()).isEmpty();
    }

    // The H2 test schema never gets this partial index, so its existence is checked here
    @Test
    void scheduledNotificationsHaveTheirUniqueBackstop() {
        String definition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_notifications_scheduled'", String.class);

        assertThat(definition)
                .startsWith("CREATE UNIQUE INDEX")
                .contains("(recipient_id, type, daily_id)")
                .contains("SESSION_REMINDER")
                .contains("DIARISTA_WINDOW_OPEN");
    }
}
