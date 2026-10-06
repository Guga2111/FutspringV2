package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordResetTokenRepository tokenRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    PasswordResetService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder, eventPublisher);
        user = User.builder().id(1L).username("alice").email("alice@example.com")
                .password(passwordEncoder.encode("oldpassword")).build();
    }

    // --- requestReset ---

    @Test
    void requestReset_unknownEmail_doesNothing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com");

        verifyNoInteractions(tokenRepository, eventPublisher);
    }

    @Test
    void requestReset_storesOnlyTheHashAndPublishesTheRawToken() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.existsByUserAndCreatedAtAfter(eq(user), any())).thenReturn(false);

        service.requestReset("alice@example.com");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<PasswordResetRequestedEvent> event = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(tokenRepository).invalidateActiveTokens(eq(user), any());
        verify(tokenRepository).save(saved.capture());
        verify(eventPublisher).publishEvent(event.capture());

        String rawToken = event.getValue().rawToken();
        assertThat(saved.getValue().getTokenHash()).isEqualTo(ResetTokens.hash(rawToken)).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getUser()).isEqualTo(user);
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(LocalDateTime.now().plusMinutes(29), LocalDateTime.now().plusMinutes(31));
        assertThat(event.getValue().email()).isEqualTo("alice@example.com");
        assertThat(event.getValue().toString()).doesNotContain(rawToken);
    }

    @Test
    void requestReset_withinCooldown_doesNotIssueAnotherToken() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.existsByUserAndCreatedAtAfter(eq(user), any())).thenReturn(true);

        service.requestReset("alice@example.com");

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // --- resetPassword ---

    @Test
    void resetPassword_validToken_changesPasswordAndInvalidatesTokens() {
        PasswordResetToken token = PasswordResetToken.builder().id(5L).user(user)
                .tokenHash(ResetTokens.hash("raw")).expiresAt(LocalDateTime.now().plusMinutes(10)).build();
        when(tokenRepository.findUsableForUpdate(eq(ResetTokens.hash("raw")), any())).thenReturn(Optional.of(token));

        service.resetPassword("raw", "newpassword");

        assertThat(passwordEncoder.matches("newpassword", user.getPassword())).isTrue();
        verify(tokenRepository).invalidateActiveTokens(eq(user), any());
    }

    @Test
    void resetPassword_unusableToken_throws400AndKeepsPassword() {
        String oldHash = user.getPassword();
        when(tokenRepository.findUsableForUpdate(anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("expired-or-used", "newpassword"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(user.getPassword()).isEqualTo(oldHash);
        verify(tokenRepository, never()).invalidateActiveTokens(any(), any());
    }
}
