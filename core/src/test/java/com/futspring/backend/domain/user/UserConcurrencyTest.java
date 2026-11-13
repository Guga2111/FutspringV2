package com.futspring.backend.domain.user;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserConcurrencyTest {
    @Autowired EntityManagerFactory factory;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;

    @org.junit.jupiter.api.Test
    void staleWriteHasAConflictResponseWithoutExposingDatabaseDetails() {
        var response = new com.futspring.backend.shared.exception.GlobalExceptionHandler().handleConcurrentUpdate(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(User.class, 1L));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).contains("Recarregue");
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "preferences", "deletion", "trial"})
    void staleProfileCannotOverwriteSecurityOrTrialChanges(String operation) {
        User original = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@concurrent.test")
                .username("Original").password("old-hash").emailVerifiedAt(Instant.EPOCH).build());
        try (var stale = factory.createEntityManager()) {
            stale.getTransaction().begin();
            User profile = stale.find(User.class, original.getId());
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                if (operation.equals("trial")) {
                    assertThat(users.markTrialUsed(original.getId(), Instant.EPOCH)).isOne();
                    return;
                }
                User current = users.findById(original.getId()).orElseThrow();
                switch (operation) {
                    case "password" -> { current.setPassword("new-hash"); current.setTokenVersion(1); }
                    case "preferences" -> current.setEmailLinkVersion(1);
                    case "deletion" -> { current.setDeletedAt(Instant.EPOCH); current.setEmail("deleted-" + current.getId() + "@invalid"); }
                }
                users.saveAndFlush(current);
            });
            profile.setUsername("Stale name");
            assertThatThrownBy(() -> stale.getTransaction().commit())
                    .hasRootCauseInstanceOf(org.hibernate.StaleStateException.class);
        }
        User kept = users.findById(original.getId()).orElseThrow();
        assertThat(kept.getUsername()).isEqualTo("Original");
        switch (operation) {
            case "password" -> { assertThat(kept.getPassword()).isEqualTo("new-hash"); assertThat(kept.getTokenVersion()).isOne(); }
            case "preferences" -> assertThat(kept.getEmailLinkVersion()).isOne();
            case "deletion" -> assertThat(kept.isDeleted()).isTrue();
            case "trial" -> assertThat(kept.getTrialUsedAt()).isEqualTo(Instant.EPOCH);
        }
        users.deleteById(original.getId());
    }
}
