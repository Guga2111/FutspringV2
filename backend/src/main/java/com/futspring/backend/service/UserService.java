package com.futspring.backend.service;

import com.futspring.backend.dto.ProfileDTO;
import com.futspring.backend.dto.UpdateProfileRequest;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> VALID_POSITIONS = Set.of(
            "GOALKEEPER", "DEFENDER", "MIDFIELDER", "FORWARD"
    );

    private final UserRepository userRepository;

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

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

    @Transactional
    public ProfileDTO uploadUserImage(Long id, MultipartFile file, String callerEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        if (!user.getEmail().equals(callerEmail)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can only upload your own image");
        }

        String filename = storeFile(file);
        deleteOldFile(user.getImage());
        user.setImage(filename);
        return ProfileDTO.from(userRepository.save(user));
    }

    @Transactional
    public ProfileDTO uploadBackgroundImage(Long id, MultipartFile file, String callerEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        if (!user.getEmail().equals(callerEmail)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can only upload your own background image");
        }

        String filename = storeFile(file);
        deleteOldFile(user.getBackgroundImage());
        user.setBackgroundImage(filename);
        return ProfileDTO.from(userRepository.save(user));
    }

    private String storeFile(MultipartFile file) {
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new AppException(HttpStatus.BAD_REQUEST, "File size exceeds 5MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/webp"))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, and WebP images are allowed");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String filename = UUID.randomUUID().toString() + extension;

        try {
            Path uploadPath = Paths.get(uploadsDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Files.copy(file.getInputStream(), uploadPath.resolve(filename));
        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }

        return filename;
    }

    private void deleteOldFile(String oldFilename) {
        if (oldFilename != null && !oldFilename.isBlank()) {
            try {
                Files.deleteIfExists(Paths.get(uploadsDir).resolve(oldFilename));
            } catch (IOException ignored) {
                // best-effort deletion
            }
        }
    }
}
