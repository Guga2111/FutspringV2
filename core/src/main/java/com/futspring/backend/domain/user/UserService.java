package com.futspring.backend.domain.user;

import com.futspring.backend.domain.auth.session.IssuedSession;
import com.futspring.backend.domain.auth.session.RefreshSessionService;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.notification.webpush.PushSubscriptionRepository;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStarsRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.user.dto.ChangePasswordRequestDTO;
import com.futspring.backend.domain.user.dto.ProfileDTO;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import com.futspring.backend.domain.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    static final int MIN_SEARCH_LENGTH = 3;
    static final int MAX_SEARCH_RESULTS = 10;

    private final UserRepository userRepository;
    private final com.futspring.backend.domain.pelada.PeladaRepository peladaRepository;
    private final FileUploadService fileUploadService;
    private final PeladaPlayerStarsRepository playerStarsRepository;
    private final UserAuthenticationHelper userAuthHelper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessionService refreshSessionService;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Username containing the query (wildcards taken literally) or the exact e-mail, any case; public fields only
    @Transactional(readOnly = true)
    public List<PublicUserDTO> searchUsers(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < MIN_SEARCH_LENGTH) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Digite pelo menos " + MIN_SEARCH_LENGTH + " caracteres para buscar");
        }
        String lower = User.normalizeEmail(q);
        return userRepository.searchByUsernameOrEmail("%" + escapeLike(lower) + "%", lower,
                        PageRequest.of(0, MAX_SEARCH_RESULTS)).stream()
                .map(PublicUserDTO::from)
                .toList();
    }

    // Escape character first, so the escapes added for % and _ aren't escaped again
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // Public profile; the email is only included for the owner
    @Transactional(readOnly = true)
    public ProfileDTO getProfile(Long id, String callerEmail) {
        User user = findUser(id);
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        if (!user.getId().equals(caller.getId()) && !peladaRepository.existsSharedPelada(id, caller.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Você não compartilha uma pelada com este jogador");
        }
        return toProfile(user, user.getId().equals(caller.getId()));
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
     * Changes the caller's password and revokes every session opened before (tokenVersion, refresh sessions included),
     * opening a new session of the new version so this device stays logged in. Every Web Push subscription goes too: this device subscribes again
     * when the app opens (the permission is still granted).
     */
    @Transactional
    public IssuedSession changePassword(ChangePasswordRequestDTO request, String callerEmail) {
        User user = userAuthHelper.getAuthenticatedUser(callerEmail);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw AppException.field("currentPassword", "Senha atual incorreta");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        pushSubscriptionRepository.deleteAllByUserId(user.getId());
        // Open chat connections (this one included) close after the commit; this device reconnects with the new token
        eventPublisher.publishEvent(new TokensRevokedEvent(user.getEmail()));
        return refreshSessionService.open(user);
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
