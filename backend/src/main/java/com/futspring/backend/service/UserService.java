package com.futspring.backend.service;

import com.futspring.backend.dto.ProfileDTO;
import com.futspring.backend.dto.UpdateProfileRequest;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> VALID_POSITIONS = Set.of(
            "GOALKEEPER", "DEFENDER", "MIDFIELDER", "FORWARD"
    );

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ProfileDTO getProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
        return ProfileDTO.from(user);
    }

    @Transactional
    public ProfileDTO updateProfile(Long id, UpdateProfileRequest request, String callerEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        if (!user.getEmail().equals(callerEmail)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can only update your own profile");
        }

        if (request.getUsername() != null) {
            userRepository.findByUsername(request.getUsername()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new AppException(HttpStatus.CONFLICT, "Username is already taken");
                }
            });
            user.setUsername(request.getUsername());
        }

        if (request.getPosition() != null) {
            if (request.getPosition().isEmpty()) {
                user.setPosition(null);
            } else {
                if (!VALID_POSITIONS.contains(request.getPosition())) {
                    throw new AppException(HttpStatus.BAD_REQUEST,
                            "Invalid position. Must be one of: GOALKEEPER, DEFENDER, MIDFIELDER, FORWARD");
                }
                user.setPosition(request.getPosition());
            }
        }

        if (request.getStars() != null) {
            user.setStars(request.getStars());
        }

        User saved = userRepository.save(user);
        return ProfileDTO.from(saved);
    }
}
