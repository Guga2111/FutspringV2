package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.notification.dto.NotificationPreferenceDTO;
import com.futspring.backend.domain.user.User;
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

/**
 * E-mail preferences per category. Only the choices that differ from NotificationCategory.defaultEmail are stored,
 * so changing a default later reaches everyone who never touched it. In-app notifications are always on.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final UserAuthenticationHelper userAuthHelper;
    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public List<NotificationPreferenceDTO> get(String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        return toDTOs(preferenceRepository.findByIdUserId(caller.getId()));
    }

    // Partial: only the categories sent change; the response has all four
    @Transactional
    public List<NotificationPreferenceDTO> update(List<NotificationPreferenceDTO> changes, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        if (changes.stream().map(NotificationPreferenceDTO::category).distinct().count() != changes.size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Categorias repetidas");
        }
        changes.forEach(change -> set(caller, change.category(), change.emailEnabled()));
        return toDTOs(preferenceRepository.findByIdUserId(caller.getId()));
    }

    // userId → category → e-mail on, defaults applied, for every user given (one query)
    @Transactional(readOnly = true)
    public Map<Long, Map<NotificationCategory, Boolean>> emailEnabled(Collection<Long> userIds) {
        Map<Long, Map<NotificationCategory, Boolean>> result = new HashMap<>();
        userIds.forEach(id -> result.put(id, defaults()));
        preferenceRepository.findByIdUserIdIn(userIds).forEach(p ->
                result.get(p.getId().getUserId()).put(p.getCategory(), p.isEmailEnabled()));
        return result;
    }

    void set(User user, NotificationCategory category, boolean emailEnabled) {
        NotificationPreferenceId id = new NotificationPreferenceId(user.getId(), category);
        if (emailEnabled == category.defaultEmail()) {
            preferenceRepository.findById(id).ifPresent(preferenceRepository::delete);
            return;
        }
        preferenceRepository.findById(id).ifPresentOrElse(
                p -> p.setEmailEnabled(emailEnabled),
                () -> preferenceRepository.save(new NotificationPreference(user, category, emailEnabled)));
    }

    private static Map<NotificationCategory, Boolean> defaults() {
        Map<NotificationCategory, Boolean> map = new EnumMap<>(NotificationCategory.class);
        Arrays.stream(NotificationCategory.values()).forEach(c -> map.put(c, c.defaultEmail()));
        return map;
    }

    private static List<NotificationPreferenceDTO> toDTOs(List<NotificationPreference> rows) {
        Map<NotificationCategory, Boolean> values = defaults();
        rows.forEach(p -> values.put(p.getCategory(), p.isEmailEnabled()));
        return values.entrySet().stream()
                .map(e -> new NotificationPreferenceDTO(e.getKey(), e.getValue()))
                .toList();
    }
}
