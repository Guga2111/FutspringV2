package com.futspring.backend.repository;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

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
}
