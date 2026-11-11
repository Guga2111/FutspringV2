package com.futspring.backend.domain.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
        assertThat(search("ALICE")).extracting(User::getUsername).containsExactly("alice_user");
    }

    @Test
    void searchByUsernameOrEmail_partialUsernameMatch() {
        assertThat(search("player")).extracting(User::getUsername).containsExactly("bob_player");
    }

    @Test
    void searchByUsernameOrEmail_exactEmailMatches() {
        assertThat(search("bob@test.org")).extracting(User::getUsername).containsExactly("bob_player");
    }

    @Test
    void searchByUsernameOrEmail_emailFragmentNeverMatches() {
        assertThat(search("bob@test")).isEmpty();
        assertThat(search("@example.com")).isEmpty();
        assertThat(search("test.org")).isEmpty();
    }

    @Test
    void searchByUsernameOrEmail_wildcardsAreLiteral() {
        assertThat(search("%%%")).isEmpty();
        assertThat(search("___")).isEmpty();

        userRepository.save(User.builder().email("ab@test.org").username("a_b").password("x").build());
        userRepository.save(User.builder().email("axb@test.org").username("axb").password("x").build());
        assertThat(search("a_b")).extracting(User::getUsername).containsExactly("a_b");
    }

    @Test
    void searchByUsernameOrEmail_deletedNeverMatches() {
        alice.setDeletedAt(Instant.now());
        userRepository.save(alice);

        assertThat(search("alice")).isEmpty();
        assertThat(search("alice@example.com")).isEmpty();
    }

    @Test
    void searchByUsernameOrEmail_noMatch_returnsEmpty() {
        assertThat(search("zzzzz")).isEmpty();
    }

    @Test
    void searchByUsernameOrEmail_respectsPageSize() {
        assertThat(userRepository.searchByUsernameOrEmail("%", "", PageRequest.of(0, 1))).hasSize(1);
    }

    // The pattern UserService builds: lower case, \, % and _ escaped
    private List<User> search(String q) {
        String lower = q.toLowerCase(Locale.ROOT);
        String escaped = lower.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return userRepository.searchByUsernameOrEmail("%" + escaped + "%", lower, PAGE);
    }

    private static final PageRequest PAGE = PageRequest.of(0, 10);
}
