package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PeladaRepositoryTest {

    @Autowired private PeladaRepository peladaRepository;
    @Autowired private UserRepository userRepository;

    private User ana;
    private User bia;
    private User cid;
    private Pelada first;
    private Pelada second;

    @BeforeEach
    void setUp() {
        ana = user("ana");
        bia = user("bia");
        cid = user("cid");
        first = pelada("A");
        first.getMembers().addAll(List.of(ana, bia));
        first.getAdmins().add(ana);
        second = pelada("B");
        second.getMembers().addAll(List.of(ana, cid));
        second.getAdmins().addAll(List.of(ana, cid));
        peladaRepository.saveAllAndFlush(List.of(first, second));
    }

    @Test
    void findMemberIds_onlyTheGivenIdsThatAreMembersOfThatPelada() {
        assertThat(peladaRepository.findMemberIds(first.getId(), List.of(ana.getId(), bia.getId(), cid.getId(), 999L)))
                .containsExactlyInAnyOrder(ana.getId(), bia.getId());
    }

    @Test
    void findAdminIds_onlyThatPelada() {
        assertThat(peladaRepository.findAdminIds(first.getId())).containsExactly(ana.getId());
        assertThat(peladaRepository.findAdminIds(second.getId())).containsExactlyInAnyOrder(ana.getId(), cid.getId());
    }

    private User user(String name) {
        return userRepository.save(User.builder().email(name + "@test.com").username(name).password("hash").build());
    }

    private Pelada pelada(String name) {
        return peladaRepository.save(Pelada.builder().name(name).dayOfWeek("FRIDAY").timeOfDay("18:00").duration(1f).creator(ana).build());
    }
}
