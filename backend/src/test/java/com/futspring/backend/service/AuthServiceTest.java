package com.futspring.backend.service;

import com.futspring.backend.config.JwtConfig;
import com.futspring.backend.dto.AuthResponseDTO;
import com.futspring.backend.dto.LoginRequestDTO;
import com.futspring.backend.dto.RegisterRequestDTO;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    JwtService jwtService;
    AuthService authService;

    @BeforeEach
    void setUp() {
        JwtConfig jwtConfig = new JwtConfig();
        ReflectionTestUtils.setField(jwtConfig, "secret",
                "test-secret-key-that-is-at-least-32-characters-long");
        ReflectionTestUtils.setField(jwtConfig, "expirationMs", 3600000L);
        jwtService = new JwtService(jwtConfig);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    // --- register ---

    @Test
    void register_success_returnsTokenAndUserDTO() {
        RegisterRequestDTO req = new RegisterRequestDTO();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        AuthResponseDTO result = authService.register(req);

        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getUser().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void register_emailAlreadyRegistered_throwsConflict() {
        RegisterRequestDTO req = new RegisterRequestDTO();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(User.builder().email("alice@example.com").build()));

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void register_passwordIsEncoded_beforePersisting() {
        RegisterRequestDTO req = new RegisterRequestDTO();
        req.setUsername("bob");
        req.setEmail("bob@example.com");
        req.setPassword("rawPassword");

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
        RegisterRequestDTO req = new RegisterRequestDTO();
        req.setUsername("carol");
        req.setEmail("carol@example.com");
        req.setPassword("password123");

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
        RegisterRequestDTO req = new RegisterRequestDTO();
        req.setUsername("dave");
        req.setEmail("dave@example.com");
        req.setPassword("password123");

        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 99L);
            return u;
        });

        AuthResponseDTO result = authService.register(req);

        assertThat(jwtService.extractEmail(result.getToken())).isEqualTo("dave@example.com");
        assertThat(jwtService.extractUserId(result.getToken())).isEqualTo(99L);
    }

    // --- login ---

    @Test
    void login_success_returnsTokenAndUserDTO() {
        User user = User.builder()
                .id(1L)
                .email("alice@example.com")
                .username("alice")
                .password(passwordEncoder.encode("password123"))
                .build();

        LoginRequestDTO req = new LoginRequestDTO();
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        AuthResponseDTO result = authService.login(req);

        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getUser().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void login_emailNotFound_throwsUnauthorized() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setEmail("nobody@example.com");
        req.setPassword("anypassword");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User user = User.builder()
                .id(1L)
                .email("alice@example.com")
                .password(passwordEncoder.encode("correctPassword"))
                .build();

        LoginRequestDTO req = new LoginRequestDTO();
        req.setEmail("alice@example.com");
        req.setPassword("wrongPassword");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void login_correctCredentials_generatesTokenWithUserId() {
        User user = User.builder()
                .id(42L)
                .email("alice@example.com")
                .username("alice")
                .password(passwordEncoder.encode("password123"))
                .build();

        LoginRequestDTO req = new LoginRequestDTO();
        req.setEmail("alice@example.com");
        req.setPassword("password123");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        AuthResponseDTO result = authService.login(req);

        assertThat(jwtService.extractUserId(result.getToken())).isEqualTo(42L);
        assertThat(jwtService.extractEmail(result.getToken())).isEqualTo("alice@example.com");
    }
}
