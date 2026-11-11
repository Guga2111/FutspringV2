package com.futspring.backend.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    // Runs on every authenticated request (AccessTokenVerifier): reads one column through the unique email index
    @Query("SELECT u.tokenVersion FROM User u WHERE u.email = :email")
    Optional<Integer> findTokenVersionByEmail(@Param("email") String email);

    // Usernames aren't unique in the database (production has legacy duplicates), so check with exists, never a single-result find
    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    // A deleted account answers 404 on every profile route
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    // The platform's operators (SUBSCRIPTION_PIX_CLAIMED goes to them)
    List<User> findByPlatformAdminTrueAndDeletedAtIsNull();

    // User search: `pattern` is a lower-case LIKE pattern for the username (wildcards escaped with a backslash
    // by UserService); the e-mail only matches exactly (lower case), never a fragment, so the search can't map e-mails
    // to accounts. Callers must only return public fields (PublicUserDTO); deleted accounts never match
    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
          AND (LOWER(u.username) LIKE :pattern ESCAPE '\\'
           OR LOWER(u.email) = :email)
        ORDER BY u.username
        """)
    List<User> searchByUsernameOrEmail(@Param("pattern") String pattern, @Param("email") String email, Pageable pageable);

    // Conditional, so two peladas created at once by the same account can't both take the trial (the row lock of the
    // UPDATE makes the second one see trial_used_at set); 1 = the trial was taken now
    @Modifying
    @Query("UPDATE User u SET u.trialUsedAt = :now WHERE u.id = :id AND u.trialUsedAt IS NULL")
    int markTrialUsed(@Param("id") Long id, @Param("now") Instant now);

    // The platform admin's user list: deleted accounts included, newest first; `q` (lower case) matches name or e-mail
    @Query(value = """
        SELECT u FROM User u
        WHERE :q = '' OR LOWER(u.username) LIKE CONCAT('%', :q, '%') OR LOWER(u.email) LIKE CONCAT('%', :q, '%')
        ORDER BY u.id DESC
        """, countQuery = """
        SELECT COUNT(u) FROM User u
        WHERE :q = '' OR LOWER(u.username) LIKE CONCAT('%', :q, '%') OR LOWER(u.email) LIKE CONCAT('%', :q, '%')
        """)
    Page<User> searchForPlatform(@Param("q") String q, Pageable pageable);

    long countByDeletedAtIsNull();

    long countByDeletedAtIsNullAndCreatedAtGreaterThanEqual(Instant since);
}
