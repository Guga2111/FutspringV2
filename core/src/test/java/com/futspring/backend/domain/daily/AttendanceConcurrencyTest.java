package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Diaristas racing for the last spot: the row lock on the daily must let exactly one in. H2 doesn't reproduce
// SELECT ... FOR UPDATE faithfully, so this runs on Postgres, without a test transaction (each confirmation
// commits its own). Skipped when Docker isn't available.
@SpringBootTest
@ActiveProfiles("prod") // no DataInitializer seed
@Testcontainers(disabledWithoutDocker = true)
class AttendanceConcurrencyTest {

    private static final int RACERS = 8;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private DailyAttendanceService attendanceService;
    @Autowired private DailyAttendanceRepository attendanceRepository;
    @Autowired private DailyRepository dailyRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void diaristasRacingForTheLastSpot_exactlyOneGetsIt() throws Exception {
        List<User> confirmed = users("in", 3);
        List<User> racers = users("racer", RACERS);
        Pelada pelada = Pelada.builder().name("Pelada").dayOfWeek("FRIDAY").timeOfDay("00:00").duration(1f)
                .numberOfTeams(2).playersPerTeam(2) // 4 spots, 3 taken
                .creator(confirmed.get(0)).build();
        pelada.setMembers(new HashSet<>(confirmed));
        pelada.getMembers().addAll(racers);
        pelada = peladaRepository.save(pelada);
        // Today at midnight in the pelada's zone: the diarista window is open
        Daily daily = dailyRepository.save(Daily.builder().pelada(pelada)
                .dailyDate(LocalDate.now(ZoneId.of(pelada.getTimeZone()))).dailyTime("00:00").build());
        for (User user : confirmed) {
            attendanceRepository.save(new DailyAttendance(daily, user, Instant.now()));
        }

        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatus>> results = new ArrayList<>();
        Long dailyId = daily.getId();
        for (User racer : racers) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    attendanceService.confirmAttendance(dailyId, racer.getEmail());
                    return HttpStatus.OK;
                } catch (AppException e) {
                    return e.getStatus();
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<HttpStatus> result : results) {
            statuses.add(result.get());
        }
        assertThat(statuses).filteredOn(HttpStatus.OK::equals).hasSize(1);
        assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(RACERS - 1);
        assertThat(attendanceRepository.countByDaily(daily)).isEqualTo(4);
    }

    private List<User> users(String prefix, int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(userRepository.save(User.builder()
                    .email(prefix + i + "@test.com").username(prefix + i).password("hash").build()));
        }
        return users;
    }
}
