package com.futspring.backend.domain.daily.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@TestPropertySource(locations = "classpath:application.properties")
class DailyRepositoryTest {

    @Autowired
    DailyRepository dailyRepository;
    @Autowired
    PeladaRepository peladaRepository;
    @Autowired
    UserRepository userRepository;

    Pelada pelada1;
    Pelada pelada2;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(User.builder()
                .email("creator@example.com")
                .username("creator")
                .password("hash")
                .build());

        pelada1 = peladaRepository.save(Pelada.builder()
                .name("Pelada 1")
                .dayOfWeek("FRIDAY")
                .timeOfDay("18:00")
                .duration(2f)
                .creator(creator)
                .build());

        pelada2 = peladaRepository.save(Pelada.builder()
                .name("Pelada 2")
                .dayOfWeek("SATURDAY")
                .timeOfDay("10:00")
                .duration(1.5f)
                .creator(creator)
                .build());
    }

    // --- existsByPeladaAndDailyDate ---

    @Test
    void existsByPeladaAndDailyDate_existingCombination_returnsTrue() {
        LocalDate date = LocalDate.of(2024, 3, 15);
        dailyRepository.save(Daily.builder()
                .pelada(pelada1)
                .dailyDate(date)
                .dailyTime("18:00")
                .build());

        assertThat(dailyRepository.existsByPeladaAndDailyDate(pelada1, date)).isTrue();
    }

    @Test
    void existsByPeladaAndDailyDate_nonExistingDate_returnsFalse() {
        assertThat(dailyRepository.existsByPeladaAndDailyDate(pelada1, LocalDate.of(2024, 12, 25))).isFalse();
    }

    @Test
    void existsByPeladaAndDailyDate_sameDateDifferentPelada_returnsFalse() {
        LocalDate date = LocalDate.of(2024, 3, 15);
        dailyRepository.save(Daily.builder()
                .pelada(pelada1)
                .dailyDate(date)
                .dailyTime("18:00")
                .build());

        // pelada2 doesn't have this date
        assertThat(dailyRepository.existsByPeladaAndDailyDate(pelada2, date)).isFalse();
    }

    // --- findByPeladaOrderByDailyDateDesc ---

    @Test
    void findByPeladaOrderByDailyDateDesc_returnsScopedToCorrectPelada() {
        dailyRepository.save(Daily.builder()
                .pelada(pelada1)
                .dailyDate(LocalDate.of(2024, 1, 1))
                .dailyTime("18:00")
                .build());

        dailyRepository.save(Daily.builder()
                .pelada(pelada2)
                .dailyDate(LocalDate.of(2024, 6, 1))
                .dailyTime("10:00")
                .build());

        List<Daily> result = dailyRepository.findByPeladaOrderByDailyDateDesc(pelada1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPelada().getId()).isEqualTo(pelada1.getId());
    }

    @Test
    void findByPeladaOrderByDailyDateDesc_orderedCorrectly() {
        dailyRepository.save(Daily.builder()
                .pelada(pelada1)
                .dailyDate(LocalDate.of(2024, 1, 1))
                .dailyTime("18:00")
                .build());
        dailyRepository.save(Daily.builder()
                .pelada(pelada1)
                .dailyDate(LocalDate.of(2024, 6, 1))
                .dailyTime("18:00")
                .build());
        dailyRepository.save(Daily.builder()
                .pelada(pelada1)
                .dailyDate(LocalDate.of(2024, 3, 15))
                .dailyTime("18:00")
                .build());

        List<Daily> result = dailyRepository.findByPeladaOrderByDailyDateDesc(pelada1);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDailyDate()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(result.get(1).getDailyDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(result.get(2).getDailyDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    // --- findNextDailies / findConfirmedDailyIds ---

    @Test
    void findNextDailies_returnsEarliestUpcomingSessionPerPelada() {
        LocalDate today = LocalDate.of(2026, 10, 4);
        Daily p1Next = dailyRepository.save(Daily.builder().pelada(pelada1).dailyDate(today.plusDays(2)).dailyTime("18:00").build());
        dailyRepository.save(Daily.builder().pelada(pelada1).dailyDate(today.plusDays(9)).dailyTime("18:00").build());
        dailyRepository.save(Daily.builder().pelada(pelada1).dailyDate(today.plusDays(1)).dailyTime("18:00")
                .status(DailyStatus.CANCELED).build());
        dailyRepository.save(Daily.builder().pelada(pelada1).dailyDate(today.minusDays(1)).dailyTime("18:00").build());
        Daily p2Next = dailyRepository.save(Daily.builder().pelada(pelada2).dailyDate(today).dailyTime("10:00")
                .status(DailyStatus.CONFIRMED).build());

        List<Daily> next = dailyRepository.findNextDailies(
                List.of(pelada1.getId(), pelada2.getId()), Set.of(DailyStatus.SCHEDULED, DailyStatus.CONFIRMED), today);

        assertThat(next).extracting(Daily::getId).containsExactlyInAnyOrder(p1Next.getId(), p2Next.getId());
    }

    @Test
    void findConfirmedDailyIds_returnsOnlyDailiesTheUserConfirmed() {
        User player = userRepository.save(User.builder().email("player@example.com").username("player").password("hash").build());
        Daily confirmed = Daily.builder().pelada(pelada1).dailyDate(LocalDate.of(2026, 10, 6)).dailyTime("18:00").build();
        confirmed.getConfirmedPlayers().add(player);
        confirmed = dailyRepository.save(confirmed);
        Daily other = dailyRepository.save(Daily.builder().pelada(pelada1).dailyDate(LocalDate.of(2026, 10, 13)).dailyTime("18:00").build());

        assertThat(dailyRepository.findConfirmedDailyIds(List.of(confirmed.getId(), other.getId()), player.getId()))
                .containsExactly(confirmed.getId());
    }
}
