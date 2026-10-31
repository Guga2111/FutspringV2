package com.futspring.backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
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
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

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
    void v8_copiesSelfRatedStarsToEveryMembership() throws SQLException {
        flyway("7").migrate();
        execute("INSERT INTO users (id, email, username, password, stars) VALUES (1, 'a@a.com', 'a', 'x', 5), (2, 'b@b.com', 'b', 'x', 2)");
        for (int id = 1; id <= 2; id++) {
            execute("INSERT INTO peladas (id, name, day_of_week, time_of_day, duration, auto_create_daily_enabled, "
                    + "number_of_teams, players_per_team, created_at, creator_id) "
                    + "VALUES (" + id + ", 'p', 'FRIDAY', '18:00', 1, false, 2, 5, now(), 1)");
        }
        execute("INSERT INTO pelada_members (pelada_id, user_id) VALUES (1, 1), (1, 2), (2, 1)");

        flyway(null).migrate();

        Map<String, Integer> stars = new LinkedHashMap<>();
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT pelada_id, user_id, stars FROM pelada_player_stars ORDER BY pelada_id, user_id")) {
            while (rs.next()) {
                stars.put(rs.getLong(1) + "-" + rs.getLong(2), rs.getInt(3));
            }
        }
        assertThat(stars).containsExactly(Map.entry("1-1", 5), Map.entry("1-2", 2), Map.entry("2-1", 5));

        // The entity no longer writes users.stars: new users get the default
        execute("INSERT INTO users (id, email, username, password) VALUES (3, 'c@c.com', 'c', 'x')");
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT stars FROM users WHERE id = 3")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }
    }

    @Test
    void v9_backfillsConfirmedAtFromDailyCreatedAt() throws SQLException {
        flyway("8").migrate();
        execute("INSERT INTO users (id, email, username, password) VALUES (1, 'a@a.com', 'a', 'x'), (2, 'b@b.com', 'b', 'x')");
        execute("INSERT INTO peladas (id, name, day_of_week, time_of_day, duration, auto_create_daily_enabled, "
                + "number_of_teams, players_per_team, created_at, creator_id) VALUES (1, 'p', 'FRIDAY', '18:00', 1, false, 2, 5, now(), 1)");
        execute("INSERT INTO dailies (id, pelada_id, daily_date, daily_time, status, is_finished, created_at) VALUES "
                + "(1, 1, '2026-10-02', '18:00', 'FINISHED', true, '2026-09-28 10:00:00'), "
                + "(2, 1, '2026-10-09', '18:00', 'SCHEDULED', false, '2026-10-05 08:30:00')");
        execute("INSERT INTO daily_confirmed_players (daily_id, user_id) VALUES (1, 1), (1, 2), (2, 1)");

        flyway(null).migrate();

        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM daily_confirmed_players p JOIN dailies d ON d.id = p.daily_id "
                     + "WHERE p.confirmed_at = d.created_at")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(3);
        }
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT time_zone, max_mensalistas FROM peladas WHERE id = 1")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("America/Sao_Paulo");
            assertThat(rs.getInt(2)).isZero();
        }

        // The previous API version inserts attendance without confirmed_at during a deploy
        execute("INSERT INTO daily_confirmed_players (daily_id, user_id) VALUES (2, 2)");
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT confirmed_at IS NOT NULL FROM daily_confirmed_players WHERE daily_id = 2 AND user_id = 2")) {
            rs.next();
            assertThat(rs.getBoolean(1)).isTrue();
        }
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

    @Test
    void v11_preservesExistingMembershipSportingHistoryAndLegacyNotifications() throws SQLException {
        flyway("10").migrate();
        execute("INSERT INTO users(id,email,username,password) VALUES(1,'legacy@test.com','Legacy','hash')");
        execute("INSERT INTO peladas(id,name,day_of_week,time_of_day,duration,auto_create_daily_enabled,number_of_teams,players_per_team,created_at,creator_id) "
                + "VALUES(1,'Existing','SATURDAY','19:00',1,false,2,5,now(),1)");
        execute("INSERT INTO pelada_members(pelada_id,user_id) VALUES(1,1)");
        execute("INSERT INTO dailies(id,pelada_id,daily_date,daily_time,status,is_finished,created_at) VALUES(1,1,current_date,'19:00','FINISHED',true,now())");
        execute("INSERT INTO user_daily_stats(id,daily_id,user_id,goals,assists,matches_played,wins,won_session) VALUES(1,1,1,2,1,3,2,true)");
        execute("INSERT INTO notifications(id,recipient_id,type,pelada_id,created_at) VALUES(1,1,'ADDED_TO_PELADA',1,now())");
        flyway(null).migrate();
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("""
                SELECT s.goals, s.assists, s.matches_played, n.type, n.invitation_id
                FROM user_daily_stats s JOIN pelada_members m ON m.user_id=s.user_id
                JOIN notifications n ON n.recipient_id=m.user_id
                """)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2); assertThat(rs.getInt(2)).isEqualTo(1); assertThat(rs.getInt(3)).isEqualTo(3);
            assertThat(rs.getString(4)).isEqualTo("ADDED_TO_PELADA"); assertThat(rs.getObject(5)).isNull();
        }
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM pelada_link_blocks")) {
            rs.next(); assertThat(rs.getInt(1)).isZero();
        }
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
