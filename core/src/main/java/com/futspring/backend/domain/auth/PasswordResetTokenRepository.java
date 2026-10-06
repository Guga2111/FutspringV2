package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // Unused and not expired. The row lock makes two concurrent resets with the same link wait for each
    // other, so the second one no longer finds it usable
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t FROM PasswordResetToken t
        WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND t.expiresAt > :now
        """)
    Optional<PasswordResetToken> findUsableForUpdate(@Param("hash") String hash, @Param("now") LocalDateTime now);

    boolean existsByUserAndCreatedAtAfter(User user, LocalDateTime after);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.user = :user AND t.usedAt IS NULL")
    int invalidateActiveTokens(@Param("user") User user, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff OR t.usedAt < :cutoff")
    int deleteStale(@Param("cutoff") LocalDateTime cutoff);
}
