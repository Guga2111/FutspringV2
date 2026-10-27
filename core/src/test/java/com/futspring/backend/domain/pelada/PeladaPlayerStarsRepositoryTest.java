package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

// The profile average is a native query: members without a row count as the default 3 stars
@DataJpaTest
class PeladaPlayerStarsRepositoryTest {

    @Autowired private PeladaPlayerStarsRepository playerStarsRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;

    private User ana;
    private User bia;
    private Pelada first;
    private Pelada second;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(User.builder().email("ana@test.com").username("ana").password("hash").build());
        bia = userRepository.save(User.builder().email("bia@test.com").username("bia").password("hash").build());
        first = pelada("A");
        second = pelada("B");
        first.getMembers().add(ana);
        second.getMembers().add(ana);
        peladaRepository.saveAll(java.util.List.of(first, second));
    }

    @Test
    void averageStarsByUser_averagesEveryPelada() {
        rate(first, ana, 5);
        rate(second, ana, 2);

        assertThat(playerStarsRepository.averageStarsByUser(ana.getId())).isEqualTo(3.5);
    }

    @Test
    void averageStarsByUser_memberWithoutRowCountsAsDefault() {
        rate(first, ana, 5);

        assertThat(playerStarsRepository.averageStarsByUser(ana.getId())).isEqualTo(4.0);
    }

    @Test
    void averageStarsByUser_withoutPeladas_isNull() {
        assertThat(playerStarsRepository.averageStarsByUser(bia.getId())).isNull();
    }

    @Test
    void findStarsByPelada_onlyThatPelada() {
        rate(first, ana, 5);
        rate(second, ana, 2);

        assertThat(playerStarsRepository.findStarsByPelada(first)).containsExactlyEntriesOf(java.util.Map.of(ana.getId(), 5));
    }

    private Pelada pelada(String name) {
        return peladaRepository.save(Pelada.builder().name(name).dayOfWeek("FRIDAY").timeOfDay("18:00").duration(1f).creator(ana).build());
    }

    private void rate(Pelada pelada, User user, int stars) {
        playerStarsRepository.saveAndFlush(PeladaPlayerStars.builder().pelada(pelada).user(user).stars(stars).build());
    }
}
