package com.futspring.backend.domain.notification.preference;

import com.futspring.backend.domain.notification.NotificationCategory;
import com.futspring.backend.domain.notification.dto.NotificationPreferenceDTO;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * E-mail and push preferences per category. Only the choices that differ from the category's defaults
 * (NotificationCategory.defaultEmail / defaultPush) are stored, so changing a default later reaches everyone who never
 * touched it. In-app notifications are always on. The e-mail's unsubscribe token only changes the e-mail.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final UserAuthenticationHelper userAuthHelper;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final UnsubscribeTokens unsubscribeTokens;

    @Transactional(readOnly = true)
    public List<NotificationPreferenceDTO> get(String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        return toDTOs(preferenceRepository.findByIdUserId(caller.getId()));
    }

    // Partial: only the categories sent change; the response has all four
    @Transactional
    public List<NotificationPreferenceDTO> update(List<NotificationPreferenceDTO> changes, String currentUserEmail) {
        User caller = userRepository.findByEmailForUpdate(currentUserEmail).orElseThrow(NotificationPreferenceService::invalidLink);
        if (changes.stream().map(NotificationPreferenceDTO::category).distinct().count() != changes.size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Categorias repetidas");
        }
        if (changes.stream().anyMatch(c -> c.emailEnabled() == null && c.pushEnabled() == null)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Informe e-mail ou push");
        }
        User locked = caller;
        if (changes.stream().anyMatch(change -> change.emailEnabled() != null)) {
            locked.setEmailLinkVersion(locked.getEmailLinkVersion() + 1);
        }
        changes.forEach(change -> set(locked, change.category(), change.emailEnabled(), change.pushEnabled()));
        return toDTOs(preferenceRepository.findByIdUserId(caller.getId()));
    }

    // userId → category → e-mail on, defaults applied, for every user given (one query)
    @Transactional(readOnly = true)
    public Map<Long, Map<NotificationCategory, Boolean>> emailEnabled(Collection<Long> userIds) {
        Map<Long, Map<NotificationCategory, Boolean>> result = new HashMap<>();
        userIds.forEach(id -> result.put(id, defaults(NotificationCategory::defaultEmail)));
        preferenceRepository.findByIdUserIdIn(userIds).forEach(p ->
                result.get(p.getId().getUserId()).put(p.getCategory(), p.isEmailEnabled()));
        return result;
    }

    // userId → category → push on, defaults applied, for every user given (one query)
    @Transactional(readOnly = true)
    public Map<Long, Map<NotificationCategory, Boolean>> pushEnabled(Collection<Long> userIds) {
        Map<Long, Map<NotificationCategory, Boolean>> result = new HashMap<>();
        userIds.forEach(id -> result.put(id, defaults(NotificationCategory::defaultPush)));
        preferenceRepository.findByIdUserIdIn(userIds).forEach(p ->
                result.get(p.getId().getUserId()).put(p.getCategory(), p.pushEnabledOrDefault()));
        return result;
    }

    // Public (the e-mail's link, or the mail provider's one-click): the token says which user and category
    @Transactional
    public NotificationPreferenceDTO unsubscribe(String token) {
        return setByToken(token, false);
    }

    private NotificationPreferenceDTO setByToken(String token, boolean emailEnabled) {
        UnsubscribeTokens.Target target = unsubscribeTokens.verify(token).orElseThrow(NotificationPreferenceService::invalidLink);
        User user = userRepository.findByIdForUpdate(target.userId()).orElseThrow(NotificationPreferenceService::invalidLink);
        if (target.version() != user.getEmailLinkVersion()) throw invalidLink();
        NotificationPreference row = set(user, target.category(), emailEnabled, null);
        return new NotificationPreferenceDTO(target.category(), emailEnabled,
                row != null ? row.pushEnabledOrDefault() : target.category().defaultPush());
    }

    private static AppException invalidLink() {
        return new AppException(HttpStatus.BAD_REQUEST, "Link inválido");
    }

    /**
     * Applies the channels given (null = unchanged). A push equal to the default is stored as null, and the row is
     * deleted when the e-mail is back to its default and the push is null. Returns the row, or null when there is none.
     */
    NotificationPreference set(User user, NotificationCategory category, Boolean emailEnabled, Boolean pushEnabled) {
        NotificationPreferenceId id = new NotificationPreferenceId(user.getId(), category);
        Optional<NotificationPreference> current = preferenceRepository.findById(id);
        boolean email = emailEnabled != null ? emailEnabled
                : current.map(NotificationPreference::isEmailEnabled).orElse(category.defaultEmail());
        Boolean push = pushEnabled != null ? (pushEnabled == category.defaultPush() ? null : pushEnabled)
                : current.map(NotificationPreference::getPushEnabled).orElse(null);
        if (email == category.defaultEmail() && push == null) {
            current.ifPresent(preferenceRepository::delete);
            return null;
        }
        if (current.isPresent()) {
            current.get().setEmailEnabled(email);
            current.get().setPushEnabled(push);
            return current.get();
        }
        return preferenceRepository.save(new NotificationPreference(user, category, email, push));
    }

    private static Map<NotificationCategory, Boolean> defaults(Predicate<NotificationCategory> channelDefault) {
        Map<NotificationCategory, Boolean> map = new EnumMap<>(NotificationCategory.class);
        Arrays.stream(NotificationCategory.values()).forEach(c -> map.put(c, channelDefault.test(c)));
        return map;
    }

    private static List<NotificationPreferenceDTO> toDTOs(List<NotificationPreference> rows) {
        Map<NotificationCategory, NotificationPreference> byCategory = new EnumMap<>(NotificationCategory.class);
        rows.forEach(p -> byCategory.put(p.getCategory(), p));
        return Arrays.stream(NotificationCategory.values())
                .map(c -> {
                    NotificationPreference p = byCategory.get(c);
                    return p == null
                            ? new NotificationPreferenceDTO(c, c.defaultEmail(), c.defaultPush())
                            : new NotificationPreferenceDTO(c, p.isEmailEnabled(), p.pushEnabledOrDefault());
                })
                .toList();
    }
}
