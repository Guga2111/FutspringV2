package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.auth.dto.AuthResponseDTO;
import com.futspring.backend.domain.auth.dto.LoginRequestDTO;
import com.futspring.backend.domain.auth.dto.RegisterRequestDTO;
import com.futspring.backend.domain.auth.session.RefreshSessionService;
import com.futspring.backend.shared.config.CorsConfig;
import com.futspring.backend.shared.config.JwtConfig;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    UserRepository userRepository;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    JwtService jwtService;
    @Mock com.futspring.backend.domain.auth.verification.EmailVerificationService verification;
    AuthService authService;
    RefreshSessionService refreshSessionService;

    @BeforeEach
    void setUp() {
        JwtConfig jwtConfig = new JwtConfig();
        ReflectionTestUtils.setField(jwtConfig, "secret",
                "test-secret-key-that-is-at-least-32-characters-long");
        ReflectionTestUtils.setField(jwtConfig, "expirationMs", 3600000L);
        jwtService = new JwtService(jwtConfig);
        // the refresh session is written to a Redis stub (its behavior is in RefreshSessionTest)
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        lenient().when(redis.opsForValue()).thenReturn(values);
        refreshSessionService = new RefreshSessionService(redis, JsonMapper.builder().build(), userRepository,
                jwtService, new CorsConfig("http://localhost:5173"), CLOCK, new SimpleMeterRegistry());
        authService = new AuthService(userRepository, passwordEncoder, refreshSessionService, CLOCK, verification);
    }

    // --- register ---

    @Test
    void register_success_returnsTokenAndUserDTO() {
        RegisterRequestDTO req = new RegisterRequestDTO("alice", "alice@example.com", "password123", LegalTerms.CURRENT_VERSION);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        authService.register(req);
        verify(verification).request("alice@example.com");
    }

    @Test
    void register_recordsTheAcceptedTermsVersionAndInstant() {
        RegisterRequestDTO req = new RegisterRequestDTO("alice", "alice@example.com", "password123", LegalTerms.CURRENT_VERSION);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        authService.register(req);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getTermsVersion()).isEqualTo(LegalTerms.CURRENT_VERSION);
        assertThat(saved.getValue().getTermsAcceptedAt()).isEqualTo(Instant.parse("2026-11-06T12:00:00Z"));
    }

    @Test
    void register_outdatedTermsVersion_throwsConflictAndSavesNothing() {
        RegisterRequestDTO req = new RegisterRequestDTO("alice", "alice@example.com", "password123", "2020-01-01");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emailAlreadyRegistered_doesNotExposeItOrChangeTheAccount() {
        RegisterRequestDTO req = new RegisterRequestDTO("alice", "alice@example.com", "password123", LegalTerms.CURRENT_VERSION);
        when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(User.builder().email(req.email()).build()));
        authService.register(req);
        verify(verification).request(req.email());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_usernameTakenInAnotherCase_throwsConflict() {
        RegisterRequestDTO req = new RegisterRequestDTO(" Alice ", "new@example.com", "password123", LegalTerms.CURRENT_VERSION);

        when(userRepository.existsByUsernameIgnoreCase("Alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_passwordIsEncoded_beforePersisting() {
        RegisterRequestDTO req = new RegisterRequestDTO("bob", "bob@example.com", "rawPassword", LegalTerms.CURRENT_VERSION);

        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 2L);
            return u;
        });

        authService.register(req);

        User saved = captor.getValue();
        assertThat(saved.getPassword()).isNotEqualTo("rawPassword");
        assertThat(passwordEncoder.matches("rawPassword", saved.getPassword())).isTrue();
    }

    @Test
    void register_savedUser_hasCorrectUsernameAndEmail() {
        RegisterRequestDTO req = new RegisterRequestDTO("carol", "carol@example.com", "password123", LegalTerms.CURRENT_VERSION);

        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 3L);
            return u;
        });

        authService.register(req);

        assertThat(captor.getValue().getUsername()).isEqualTo("carol");
        assertThat(captor.getValue().getEmail()).isEqualTo("carol@example.com");
    }

    @Test
    void register_generatesToken_withSavedUserIdAndEmail() {
        RegisterRequestDTO req = new RegisterRequestDTO("dave", "dave@example.com", "password123", LegalTerms.CURRENT_VERSION);

        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 99L);
            return u;
        });

        authService.register(req);
        verify(verification).request("dave@example.com");
    }

    // --- login ---

    @Test
    void login_success_returnsTokenAndUserDTO() {
        User user = User.builder().emailVerifiedAt(java.time.Instant.EPOCH)
                .id(1L)
                .email("alice@example.com")
                .username("alice")
                .password(passwordEncoder.encode("password123"))
                .build();

        LoginRequestDTO req = new LoginRequestDTO("alice@example.com", "password123");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        AuthResponseDTO result = authService.login(req).auth();

        assertThat(result.token()).isNotBlank();
        assertThat(result.user().email()).isEqualTo("alice@example.com");
    }

    @Test
    void login_emailNotFound_throwsUnauthorized() {
        LoginRequestDTO req = new LoginRequestDTO("nobody@example.com", "anypassword");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        PasswordEncoder spyEncoder = spy(new BCryptPasswordEncoder());
        AuthService service = new AuthService(userRepository, spyEncoder, refreshSessionService, CLOCK, verification);

        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        // an unknown e-mail still runs BCrypt, against a real cost-10 hash, so it takes as long as a wrong password
        verify(spyEncoder).matches("anypassword", AuthService.DUMMY_HASH);
        assertThat(AuthService.DUMMY_HASH).startsWith("$2a$10$").hasSize(60);
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = User.builder().emailVerifiedAt(java.time.Instant.EPOCH)
                .id(1L)
                .email("alice@example.com")
                .password(passwordEncoder.encode("correctPassword"))
                .build();

        LoginRequestDTO req = new LoginRequestDTO("alice@example.com", "wrongPassword");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void login_correctCredentials_generatesTokenWithUserId() {
        User user = User.builder().emailVerifiedAt(java.time.Instant.EPOCH)
                .id(42L)
                .email("alice@example.com")
                .username("alice")
                .password(passwordEncoder.encode("password123"))
                .build();

        LoginRequestDTO req = new LoginRequestDTO("alice@example.com", "password123");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        AuthResponseDTO result = authService.login(req).auth();

        assertThat(jwtService.extractAllClaims(result.token()).get("userId", Long.class)).isEqualTo(42L);
        assertThat(jwtService.extractAllClaims(result.token()).getSubject()).isEqualTo("alice@example.com");
    }
}
