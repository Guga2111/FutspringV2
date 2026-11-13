package com.futspring.backend.domain.user;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.attendance.DailyAttendance;
import com.futspring.backend.domain.daily.team.Team;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.team.TeamRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.pelada.PeladaService;
import com.futspring.backend.shared.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Account deletion on PostgreSQL: the native row locks and bulk statements, under real transactions. */
@SpringBootTest
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class AccountDeletionConcurrencyTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword); r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); r.add("spring.flyway.enabled", () -> "true");
    }
    static final String PASSWORD = "senha-atual-123";
    @Autowired AccountDeletionService deletion;
    @Autowired PeladaService peladaService;
    @Autowired PeladaRepository peladas;
    @Autowired UserRepository users;
    @Autowired DailyRepository dailies;
    @Autowired DailyAttendanceRepository attendance;
    @Autowired TeamRepository teams;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    User user(String name) {
        return users.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH).email(UUID.randomUUID() + "@account.test").username(name)
                .password(passwordEncoder.encode(PASSWORD)).build());
    }

    @Test void deletionLeavesEveryPeladaAndUpcomingSessionAndKeepsTheRow() {
        User owner = user("Dono"), leaving = user("Saindo");
        Pelada pelada = peladas.save(Pelada.builder().name("Pelada").dayOfWeek("SUNDAY").timeOfDay("18:00").duration(1f)
                .creator(owner).members(new HashSet<>(Set.of(owner, leaving))).admins(new HashSet<>(Set.of(owner, leaving))).build());
        Daily daily = dailies.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.of(2030, 1, 6)).dailyTime("18:00").build());
        attendance.save(new DailyAttendance(daily, leaving, Instant.now()));
        teams.save(Team.builder().daily(daily).name("Time 1").players(new HashSet<>(Set.of(owner, leaving))).build());

        deletion.deleteAccount(PASSWORD, leaving.getEmail());

        assertThat(peladas.existsByIdAndMembers_Id(pelada.getId(), leaving.getId())).isFalse();
        assertThat(peladas.existsByIdAndAdmins_Id(pelada.getId(), leaving.getId())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from daily_confirmed_players where user_id = ?", Long.class, leaving.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from team_players where user_id = ?", Long.class, leaving.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from team_players where user_id = ?", Long.class, owner.getId())).isOne();
        assertThat(users.findById(leaving.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    // The creator hands the pelada to the leaving admin while they delete their account: either the transfer
    // wins (the deletion sees the new blocker, 409) or the deletion wins (the target is no longer a member, 404)
    @Test void deletionRacingATransferToTheLeavingUserNeverLeavesADeletedCreator() throws Exception {
        User owner = user("Dono"), leaving = user("Saindo");
        Pelada pelada = peladas.save(Pelada.builder().name("Pelada").dayOfWeek("SUNDAY").timeOfDay("18:00").duration(1f)
                .creator(owner).members(new HashSet<>(Set.of(owner, leaving))).admins(new HashSet<>(Set.of(owner, leaving))).build());

        List<HttpStatus> result = race(() -> deletion.deleteAccount(PASSWORD, leaving.getEmail()),
                () -> peladaService.transferCreator(pelada.getId(), leaving.getId(), owner.getEmail()));

        assertThat(result).isIn(List.of(HttpStatus.OK, HttpStatus.NOT_FOUND), List.of(HttpStatus.CONFLICT, HttpStatus.OK));
        Long creatorId = jdbc.queryForObject("select creator_id from peladas where id = ?", Long.class, pelada.getId());
        assertThat(users.findById(creatorId).orElseThrow().getDeletedAt()).isNull();
    }

    List<HttpStatus> race(Runnable first, Runnable second) throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<HttpStatus>> futures = new ArrayList<>();
            for (Runnable action : List.of(first, second)) futures.add(pool.submit(() -> {
                start.await();
                try { action.run(); return HttpStatus.OK; } catch (AppException e) { return e.getStatus(); }
            }));
            start.countDown();
            List<HttpStatus> result = new ArrayList<>();
            for (var future : futures) result.add(future.get(20, TimeUnit.SECONDS));
            return result;
        }
    }
}
