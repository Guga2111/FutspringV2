package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.auth.dto.AuthResponseDTO;
import com.futspring.backend.domain.auth.dto.LoginRequestDTO;
import com.futspring.backend.domain.auth.dto.RegisterRequestDTO;
import com.futspring.backend.domain.auth.dto.UserResponseDTO;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponseDTO register(RegisterRequestDTO request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(HttpStatus.CONFLICT, "Este e-mail já está cadastrado");
        }
        // Usernames are unique (case-insensitive), as UserService.updateProfile already enforces
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername().trim())) {
            throw new AppException(HttpStatus.CONFLICT, "Este nome de usuário já está em uso");
        }

        User user = User.builder()
                .username(request.getUsername().trim())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved.getId(), saved.getEmail(), saved.getTokenVersion());
        return new AuthResponseDTO(token, UserResponseDTO.from(saved));
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getTokenVersion());
        return new AuthResponseDTO(token, UserResponseDTO.from(user));
    }
}
