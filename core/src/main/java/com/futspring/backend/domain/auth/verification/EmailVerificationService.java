package com.futspring.backend.domain.auth.verification;

import com.futspring.backend.domain.auth.SecretTokens;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.domain.user.TokensRevokedEvent;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final Duration TTL = Duration.ofMinutes(30);
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entities;

    // No account information in the response. The row lock serializes resends and verification.
    @Transactional
    public void request(String email) {
        users.findByEmailForUpdate(email).ifPresent(user -> {
            // OSIV may already hold the user from login/registration. Reload after locking, before checking state.
            entities.refresh(user);
            if (user.isDeleted() || user.getEmailVerifiedAt() != null) return;
            var now = clock.instant();
            if (user.getEmailVerificationSentAt() != null && now.isBefore(user.getEmailVerificationSentAt().plusSeconds(60))) return;
            String token = SecretTokens.generate();
            user.setEmailVerificationHash(SecretTokens.hash(token));
            user.setEmailVerificationExpiresAt(now.plus(TTL));
            user.setEmailVerificationSentAt(now);
            events.publishEvent(new EmailVerificationRequested(user.getEmail(), user.getUsername(), token));
        });
    }

    @Transactional
    public void verify(String token, String password) {
        var user = users.findByVerificationHashForUpdate(SecretTokens.hash(token))
                .map(locked -> { entities.refresh(locked); return locked; })
                .filter(u -> u.getEmailVerifiedAt() == null && u.getEmailVerificationExpiresAt() != null
                        && clock.instant().isBefore(u.getEmailVerificationExpiresAt()))
                .orElseThrow(EmailVerificationService::invalid);
        // Prevent mail scanners and pre-registration attacks: the recipient must also know the account's password.
        if (!passwords.matches(password, user.getPassword())) throw invalid();
        user.setEmailVerifiedAt(clock.instant());
        user.setEmailVerificationHash(null);
        user.setEmailVerificationExpiresAt(null);
        user.setEmailVerificationSentAt(null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        events.publishEvent(new TokensRevokedEvent(user.getEmail()));
    }
    private static AppException invalid() {
        return new AppException(HttpStatus.BAD_REQUEST, "Link inválido, expirado ou senha incorreta. Peça um novo link se necessário.");
    }
}
