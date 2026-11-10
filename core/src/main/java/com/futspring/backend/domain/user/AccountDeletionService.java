package com.futspring.backend.domain.user;

import com.futspring.backend.domain.auth.passwordreset.PasswordResetTokenRepository;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.finance.FinancePreservation;
import com.futspring.backend.domain.notification.NotificationPreferenceRepository;
import com.futspring.backend.domain.notification.NotificationRepository;
import com.futspring.backend.domain.notification.PushSubscriptionRepository;
import com.futspring.backend.domain.pelada.PeladaRef;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.pelada.PeladaService;
import com.futspring.backend.domain.tour.UserTourRepository;
import com.futspring.backend.domain.user.dto.DeletionBlockerDTO;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Deletes the caller's account by anonymizing it: the users row stays (past matches, awards, rankings, chat and
 * other people's finance point at it) as "Ex-jogador", and each domain drops what is personal or current.
 * Blocked while the user created a pelada or is its only responsável financeiro.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    public static final String DELETED_USERNAME = "Ex-jogador";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAuthenticationHelper userAuthHelper;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final PeladaRepository peladaRepository;
    private final PeladaService peladaService;
    private final FinancePreservation financePreservation;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserTourRepository userTourRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final FileUploadService fileUploadService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<DeletionBlockerDTO> blockers(String callerEmail) {
        return blockers(userAuthHelper.getAuthenticatedUser(callerEmail));
    }

    @Transactional
    public void deleteAccount(String password, String callerEmail) {
        User user = userAuthHelper.getAuthenticatedUser(callerEmail);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw AppException.field("password", "Senha incorreta");
        }
        // The same first lock as the member routes, so no transfer or appointment changes the blockers meanwhile
        peladaRepository.lockIdsByMemberId(user.getId());
        List<DeletionBlockerDTO> blockers = blockers(user);
        if (!blockers.isEmpty()) {
            throw new AppException(HttpStatus.CONFLICT, message(blockers.getFirst()));
        }

        peladaService.removeAccountFromPeladas(user, DELETED_USERNAME);
        notificationRepository.deleteByRecipient(user);
        notificationPreferenceRepository.deleteByUserId(user.getId());
        passwordResetTokenRepository.deleteByUser(user);
        userTourRepository.deleteByUserId(user.getId());
        pushSubscriptionRepository.deleteAllByUserId(user.getId());

        fileUploadService.deleteImageAfterCommit(user.getImage());
        fileUploadService.deleteImageAfterCommit(user.getBackgroundImage());
        user.setEmail("deleted-" + user.getId() + "@invalid");
        user.setPassword(passwordEncoder.encode(randomSecret()));
        user.setUsername(DELETED_USERNAME);
        user.setImage(null);
        user.setBackgroundImage(null);
        user.setPosition(null);
        // Every token dies: the version no longer matches and the e-mail in it no longer exists
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setDeletedAt(clock.instant());
        userRepository.save(user);
    }

    private List<DeletionBlockerDTO> blockers(User user) {
        List<DeletionBlockerDTO> blockers = new ArrayList<>();
        for (PeladaRef pelada : peladaRepository.findCreatedBy(user.getId())) {
            blockers.add(new DeletionBlockerDTO(DeletionBlockerType.CREATOR, pelada.id(), pelada.name()));
        }
        for (PeladaRef pelada : financePreservation.soleManagedPeladas(user.getId())) {
            blockers.add(new DeletionBlockerDTO(DeletionBlockerType.SOLE_FINANCE_MANAGER, pelada.id(), pelada.name()));
        }
        return blockers;
    }

    private static String message(DeletionBlockerDTO blocker) {
        return switch (blocker.type()) {
            case CREATOR -> "Transfira ou exclua a " + blocker.peladaName() + " antes de excluir a conta";
            case SOLE_FINANCE_MANAGER -> "Designe outro responsável financeiro da " + blocker.peladaName()
                    + " antes de excluir a conta";
        };
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }
}
