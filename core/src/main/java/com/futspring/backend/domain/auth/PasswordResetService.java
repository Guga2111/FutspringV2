package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Forgot / reset password. The request never reveals whether the e-mail has an account: it behaves the
 * same either way and the e-mail is sent after the commit (PasswordResetEmailListener).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    static final Duration REQUEST_COOLDOWN = Duration.ofMinutes(1);
    static final String INVALID_LINK_MESSAGE = "Link inválido ou expirado. Peça um novo.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email)
                .filter(this::isOutsideCooldown)
                .ifPresent(this::issueToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = tokenRepository.findUsableForUpdate(ResetTokens.hash(rawToken), now)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, INVALID_LINK_MESSAGE));

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        // Revokes every JWT issued before (AccessTokenVerifier): the old sessions are logged out
        user.setTokenVersion(user.getTokenVersion() + 1);
        // Marks this token and any other pending one as used
        tokenRepository.invalidateActiveTokens(user, now);
    }

    // At most one e-mail per user per minute, whatever the IP (the rate limit is per IP)
    private boolean isOutsideCooldown(User user) {
        return !tokenRepository.existsByUserAndCreatedAtAfter(user, LocalDateTime.now().minus(REQUEST_COOLDOWN));
    }

    // Only the latest link works: previous ones are invalidated
    private void issueToken(User user) {
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.invalidateActiveTokens(user, now);

        String rawToken = ResetTokens.generate();
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(ResetTokens.hash(rawToken))
                .expiresAt(now.plus(TOKEN_TTL))
                .build());
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), user.getUsername(), rawToken));
    }
}
