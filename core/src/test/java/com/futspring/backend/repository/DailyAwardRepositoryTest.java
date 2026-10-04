package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.DailyAward;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// The award counts are native UNION queries over the winner join tables
@DataJpaTest
class DailyAwardRepositoryTest {

    @Autowired private DailyAwardRepository dailyAwardRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private DailyRepository dailyRepository;

    private User ana;
    private User bia;
    private Pelada pelada;
    private Pelada other;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(User.builder().email("ana@test.com").username("ana").password("hash").build());
        bia = userRepository.save(User.builder().email("bia@test.com").username("bia").password("hash").build());
        pelada = peladaRepository.save(Pelada.builder().name("P").dayOfWeek("FRIDAY").timeOfDay("18:00").duration(1f).creator(ana).build());
        other = peladaRepository.save(Pelada.builder().name("O").dayOfWeek("MONDAY").timeOfDay("18:00").duration(1f).creator(ana).build());

        award(pelada, List.of(ana), List.of(ana, bia), List.of(bia), List.of());
        award(pelada, List.of(ana), List.of(), List.of(), List.of(bia));
        award(other, List.of(ana), List.of(), List.of(), List.of());
    }

    @Test
    void countAwardsByUser_countsEveryPelada() {
        Map<String, Integer> counts = toMap(dailyAwardRepository.countAwardsByUser(ana.getId()));

        assertThat(counts).containsEntry("ARTILHEIRO", 3).containsEntry("GARCOM", 1)
                .containsEntry("PUSKAS", 0).containsEntry("BOLA_MURCHA", 0);
    }

    @Test
    void countAwardsByUserAndPelada_onlyThatPelada() {
        Map<String, Integer> counts = toMap(dailyAwardRepository.countAwardsByUserAndPelada(ana.getId(), pelada.getId()));

        assertThat(counts).containsEntry("ARTILHEIRO", 2).containsEntry("GARCOM", 1);
    }

    @Test
    void countWinnersByPelada_groupsByCategoryAndUser() {
        List<Object[]> rows = dailyAwardRepository.countWinnersByPelada(pelada.getId());

        Map<String, Integer> byKey = rows.stream().collect(Collectors.toMap(
                r -> r[0] + ":" + r[2], r -> ((Number) r[4]).intValue()));
        assertThat(byKey).containsOnly(
                Map.entry("ARTILHEIRO:ana", 2),
                Map.entry("GARCOM:ana", 1),
                Map.entry("GARCOM:bia", 1),
                Map.entry("PUSKAS:bia", 1),
                Map.entry("BOLA_MURCHA:bia", 1));
    }

    private void award(Pelada p, List<User> artilheiro, List<User> garcom, List<User> puskas, List<User> wiltball) {
        Daily daily = dailyRepository.save(Daily.builder().pelada(p).dailyDate(LocalDate.now()).dailyTime("18:00").build());
        dailyAwardRepository.save(DailyAward.builder().daily(daily)
                .artilheiroWinners(new ArrayList<>(artilheiro))
                .garcomWinners(new ArrayList<>(garcom))
                .puskasWinners(new ArrayList<>(puskas))
                .wiltballWinners(new ArrayList<>(wiltball))
                .build());
    }

    private static Map<String, Integer> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(r -> (String) r[0], r -> ((Number) r[1]).intValue()));
    }
}
