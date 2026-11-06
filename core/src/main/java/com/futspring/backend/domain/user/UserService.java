package com.futspring.backend.domain.user;

import com.futspring.backend.domain.auth.JwtService;
import com.futspring.backend.domain.auth.dto.AuthResponseDTO;
import com.futspring.backend.domain.auth.dto.UserResponseDTO;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.pelada.PeladaPlayerStarsRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.user.dto.ChangePasswordRequestDTO;
import com.futspring.backend.domain.user.dto.ProfileDTO;
import com.futspring.backend.domain.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FileUploadService fileUploadService;
    private final PeladaPlayerStarsRepository playerStarsRepository;
    private final UserAuthenticationHelper userAuthHelper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Public profile; the email is only included for the owner
    @Transactional(readOnly = true)
    public ProfileDTO getProfile(Long id, String callerEmail) {
        User user = findUser(id);
        return toProfile(user, user.getEmail().equals(callerEmail));
    }

    @Transactional
    public ProfileDTO updateProfile(Long id, UpdateProfileRequest request, String callerEmail) {
        User user = findOwnUser(id, callerEmail);

        if (request.username() != null) {
            String username = request.username().trim();
            // Keeping the current name is always allowed, even if a legacy duplicate exists
            if (!username.equals(user.getUsername())
                    && userRepository.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
                throw new AppException(HttpStatus.CONFLICT, "Este nome de usuário já está em uso");
            }
            user.setUsername(username);
        }

        // Allowed values are validated on UpdateProfileRequest; empty clears the position
        if (request.position() != null) {
            user.setPosition(request.position().isEmpty() ? null : request.position());
        }

        return toProfile(userRepository.save(user), true);
    }

    @Transactional
    public ProfileDTO uploadUserImage(Long id, MultipartFile file, String callerEmail) {
        User user = findOwnUser(id, callerEmail);
        String filename = fileUploadService.uploadImage(file);
        fileUploadService.deleteImageAfterCommit(user.getImage());
        user.setImage(filename);
        return toProfile(userRepository.save(user), true);
    }

    @Transactional
    public ProfileDTO uploadBackgroundImage(Long id, MultipartFile file, String callerEmail) {
        User user = findOwnUser(id, callerEmail);
        String filename = fileUploadService.uploadImage(file);
        fileUploadService.deleteImageAfterCommit(user.getBackgroundImage());
        user.setBackgroundImage(filename);
        return toProfile(userRepository.save(user), true);
    }

    /**
     * Changes the caller's password and revokes every session opened before (tokenVersion), answering a token of the
     * new version so this device stays logged in.
     */
    @Transactional
    public AuthResponseDTO changePassword(ChangePasswordRequestDTO request, String callerEmail) {
        User user = userAuthHelper.getAuthenticatedUser(callerEmail);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw AppException.field("currentPassword", "Senha atual incorreta");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getTokenVersion());
        return new AuthResponseDTO(token, UserResponseDTO.from(user));
    }

    // Profile stars: the rounded average of the user's stars in every pelada they belong to
    private ProfileDTO toProfile(User user, boolean isSelf) {
        Double average = playerStarsRepository.averageStarsByUser(user.getId());
        return ProfileDTO.from(user, isSelf, average == null ? null : (int) Math.round(average));
    }

    private User findUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
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
