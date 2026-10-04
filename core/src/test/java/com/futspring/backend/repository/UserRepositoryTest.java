package com.futspring.backend.repository;

import com.futspring.backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@TestPropertySource(locations = "classpath:application.properties")
class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    User alice;
    User bob;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(User.builder()
                .email("alice@example.com")
                .username("alice_user")
                .password("hash1")
                .build());

        bob = userRepository.save(User.builder()
                .email("bob@test.org")
                .username("bob_player")
                .password("hash2")
                .build());
    }

    // --- findByEmail ---

    @Test
    void findByEmail_existingEmail_returnsUser() {
        Optional<User> result = userRepository.findByEmail("alice@example.com");
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice_user");
    }

    @Test
    void findByEmail_unknownEmail_returnsEmpty() {
        Optional<User> result = userRepository.findByEmail("unknown@example.com");
        assertThat(result).isEmpty();
    }

    // --- existsByUsernameIgnoreCase ---

    @Test
    void existsByUsernameIgnoreCase_matchesAnyCase() {
        assertThat(userRepository.existsByUsernameIgnoreCase("bob_player")).isTrue();
        assertThat(userRepository.existsByUsernameIgnoreCase("BOB_Player")).isTrue();
        assertThat(userRepository.existsByUsernameIgnoreCase("nobody")).isFalse();
    }

    @Test
    void existsByUsernameIgnoreCase_withDuplicateUsernames_doesNotThrow() {
        userRepository.save(User.builder().username("bob_player").email("bob2@test.org").password("x").build());

        assertThat(userRepository.existsByUsernameIgnoreCase("bob_player")).isTrue();
    }

    @Test
    void existsByUsernameIgnoreCaseAndIdNot_ignoresTheGivenUser() {
        Long bobId = bob.getId();

        assertThat(userRepository.existsByUsernameIgnoreCaseAndIdNot("Bob_Player", bobId)).isFalse();
        assertThat(userRepository.existsByUsernameIgnoreCaseAndIdNot("Bob_Player", bobId + 1000)).isTrue();
    }

    // --- searchByUsernameOrEmail ---

    @Test
    void searchByUsernameOrEmail_caseInsensitiveUsernameMatch() {
        List<User> results = userRepository.searchByUsernameOrEmail("ALICE", PAGE);
        assertThat(results).extracting(User::getUsername).contains("alice_user");
    }

    @Test
    void searchByUsernameOrEmail_emailMatch() {
        List<User> results = userRepository.searchByUsernameOrEmail("bob@test", PAGE);
        assertThat(results).extracting(User::getEmail).contains("bob@test.org");
    }

    @Test
    void searchByUsernameOrEmail_partialMatch() {
        List<User> results = userRepository.searchByUsernameOrEmail("player", PAGE);
        assertThat(results).extracting(User::getUsername).contains("bob_player");
    }

    @Test
    void searchByUsernameOrEmail_noMatch_returnsEmpty() {
        List<User> results = userRepository.searchByUsernameOrEmail("zzzzz", PAGE);
        assertThat(results).isEmpty();
    }

    @Test
    void searchByUsernameOrEmail_noDuplicates() {
        // "example" matches alice's email; "alice" matches alice's username — only one result
        List<User> results = userRepository.searchByUsernameOrEmail("alice", PAGE);
        long distinctIds = results.stream().map(User::getId).distinct().count();
        assertThat(distinctIds).isEqualTo(results.size());
    }

    @Test
    void searchByUsernameOrEmail_respectsPageSize() {
        List<User> results = userRepository.searchByUsernameOrEmail("", PageRequest.of(0, 1));
        assertThat(results).hasSize(1);
    }

    private static final PageRequest PAGE = PageRequest.of(0, 10);
}
