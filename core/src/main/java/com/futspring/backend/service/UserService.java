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
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FileUploadService fileUploadService;

    // Public profile; the email is only included for the owner
    @Transactional(readOnly = true)
    public ProfileDTO getProfile(Long id, String callerEmail) {
        User user = findUser(id);
        return ProfileDTO.from(user, user.getEmail().equals(callerEmail));
    }

    @Transactional
    public ProfileDTO updateProfile(Long id, UpdateProfileRequest request, String callerEmail) {
        User user = findOwnUser(id, callerEmail);

        if (request.getUsername() != null) {
            String username = request.getUsername().trim();
            userRepository.findByUsername(username).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new AppException(HttpStatus.CONFLICT, "Este nome de usuário já está em uso");
                }
            });
            user.setUsername(username);
        }

        // Allowed values are validated on UpdateProfileRequest; empty clears the position
        if (request.getPosition() != null) {
            user.setPosition(request.getPosition().isEmpty() ? null : request.getPosition());
        }

        if (request.getStars() != null) {
            user.setStars(request.getStars());
        }

        return ProfileDTO.from(userRepository.save(user), true);
    }

    @Transactional
    public ProfileDTO uploadUserImage(Long id, MultipartFile file, String callerEmail) {
        User user = findOwnUser(id, callerEmail);
        String filename = fileUploadService.uploadImage(file);
        fileUploadService.deleteImageAfterCommit(user.getImage());
        user.setImage(filename);
        return ProfileDTO.from(userRepository.save(user), true);
    }

    @Transactional
    public ProfileDTO uploadBackgroundImage(Long id, MultipartFile file, String callerEmail) {
        User user = findOwnUser(id, callerEmail);
        String filename = fileUploadService.uploadImage(file);
        fileUploadService.deleteImageAfterCommit(user.getBackgroundImage());
        user.setBackgroundImage(filename);
        return ProfileDTO.from(userRepository.save(user), true);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    private User findOwnUser(Long id, String callerEmail) {
        User user = findUser(id);
        if (!user.getEmail().equals(callerEmail)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Você só pode alterar o seu próprio perfil");
        }
        return user;
    }
}
