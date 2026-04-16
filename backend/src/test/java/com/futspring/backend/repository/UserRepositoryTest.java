package com.futspring.backend.repository;

import com.futspring.backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

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

    // --- findByUsername ---

    @Test
    void findByUsername_existingUsername_returnsUser() {
        Optional<User> result = userRepository.findByUsername("bob_player");
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("bob@test.org");
    }

    @Test
    void findByUsername_unknownUsername_returnsEmpty() {
        Optional<User> result = userRepository.findByUsername("nobody");
        assertThat(result).isEmpty();
    }

    // --- searchByUsernameOrEmail ---

    @Test
    void searchByUsernameOrEmail_caseInsensitiveUsernameMatch() {
        List<User> results = userRepository.searchByUsernameOrEmail("ALICE");
        assertThat(results).extracting(User::getUsername).contains("alice_user");
    }

    @Test
    void searchByUsernameOrEmail_emailMatch() {
        List<User> results = userRepository.searchByUsernameOrEmail("bob@test");
        assertThat(results).extracting(User::getEmail).contains("bob@test.org");
    }

    @Test
    void searchByUsernameOrEmail_partialMatch() {
        List<User> results = userRepository.searchByUsernameOrEmail("player");
        assertThat(results).extracting(User::getUsername).contains("bob_player");
    }

    @Test
    void searchByUsernameOrEmail_noMatch_returnsEmpty() {
        List<User> results = userRepository.searchByUsernameOrEmail("zzzzz");
        assertThat(results).isEmpty();
    }

    @Test
    void searchByUsernameOrEmail_noDuplicates() {
        // "example" matches alice's email; "alice" matches alice's username — only one result
        List<User> results = userRepository.searchByUsernameOrEmail("alice");
        long distinctIds = results.stream().map(User::getId).distinct().count();
        assertThat(distinctIds).isEqualTo(results.size());
    }
}
