package com.futspring.backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs data-changing migrations against rows shaped like production's. Skipped when Docker isn't available.
@Testcontainers(disabledWithoutDocker = true)
class MigrationDataTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @BeforeEach
    void cleanDatabase() {
        flyway(null).clean();
    }

    @Test
    void v5_normalizesPortugueseDaysOfWeek() throws SQLException {
        flyway("4").migrate();
        execute("INSERT INTO users (id, email, username, password, stars) VALUES (1, 'a@a.com', 'a', 'x', 3)");
        String[] days = {"Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sabado", "Domingo", "sábado", "FRIDAY", "monday", "???"};
        for (int i = 0; i < days.length; i++) {
            execute("INSERT INTO peladas (id, name, day_of_week, time_of_day, duration, auto_create_daily_enabled, "
                    + "number_of_teams, players_per_team, created_at, creator_id) "
                    + "VALUES (" + (i + 1) + ", 'p', '" + days[i] + "', '18:00', 1, false, 2, 5, now(), 1)");
        }

        flyway(null).migrate();

        Map<Long, String> result = new LinkedHashMap<>();
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, day_of_week FROM peladas ORDER BY id")) {
            while (rs.next()) {
                result.put(rs.getLong(1), rs.getString(2));
            }
        }
        assertThat(result.values()).containsExactly(
                "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY", "SATURDAY",
                "FRIDAY", "MONDAY", "???");
    }

    @Test
    void v4_dropsEmptyLegacyAwardColumns() throws SQLException {
        flyway("3").migrate();
        execute("ALTER TABLE daily_awards ADD COLUMN puskas_winner_id bigint, ADD COLUMN wiltball_winner_id bigint");

        flyway(null).migrate();

        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM information_schema.columns "
                     + "WHERE table_name = 'daily_awards' AND column_name LIKE '%winner_id'")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    void v4_refusesToDropLegacyColumnsWithValues() throws SQLException {
        flyway("3").migrate();
        execute("ALTER TABLE daily_awards ADD COLUMN puskas_winner_id bigint");
        execute("INSERT INTO users (id, email, username, password, stars) VALUES (1, 'a@a.com', 'a', 'x', 3)");
        execute("INSERT INTO peladas (id, name, day_of_week, time_of_day, duration, auto_create_daily_enabled, "
                + "number_of_teams, players_per_team, created_at, creator_id) VALUES (1, 'p', 'FRIDAY', '18:00', 1, false, 2, 5, now(), 1)");
        execute("INSERT INTO dailies (id, pelada_id, daily_date, daily_time, status, is_finished, created_at) "
                + "VALUES (1, 1, current_date, '18:00', 'FINISHED', true, now())");
        execute("INSERT INTO daily_awards (id, daily_id, puskas_winner_id) VALUES (1, 1, 1)");

        assertThatThrownBy(() -> flyway(null).migrate()).isInstanceOf(FlywayException.class);
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false);
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void execute(String sql) throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
