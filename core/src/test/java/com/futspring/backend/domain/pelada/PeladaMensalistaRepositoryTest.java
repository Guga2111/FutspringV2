package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PeladaMensalistaRepositoryTest {

    @Autowired private PeladaMensalistaRepository mensalistaRepository;
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
    }

    @Test
    void lookups_areScopedToThePelada() {
        mark(first, ana);
        mark(first, bia);
        mark(second, ana);

        assertThat(mensalistaRepository.findUserIdsByPelada(first)).containsExactlyInAnyOrder(ana.getId(), bia.getId());
        assertThat(mensalistaRepository.countByPelada(second)).isEqualTo(1);
        assertThat(mensalistaRepository.existsByPeladaAndUser(second, bia)).isFalse();
        assertThat(mensalistaRepository.findPeladaIdsByUserAndPeladaIn(bia.getId(), List.of(first.getId(), second.getId())))
                .containsExactly(first.getId());
    }

    @Test
    void deletes_byMemberAndByPelada() {
        mark(first, ana);
        mark(first, bia);
        mark(second, ana);

        mensalistaRepository.deleteByPeladaAndUser(first, ana);
        mensalistaRepository.deleteByPelada(second);

        assertThat(mensalistaRepository.findUserIdsByPelada(first)).containsExactly(bia.getId());
        assertThat(mensalistaRepository.countByPelada(second)).isZero();
    }

    private Pelada pelada(String name) {
        return peladaRepository.save(Pelada.builder().name(name).dayOfWeek("FRIDAY").timeOfDay("18:00").duration(1f).creator(ana).build());
    }

    private void mark(Pelada pelada, User user) {
        mensalistaRepository.saveAndFlush(PeladaMensalista.builder().pelada(pelada).user(user).build());
    }
}
