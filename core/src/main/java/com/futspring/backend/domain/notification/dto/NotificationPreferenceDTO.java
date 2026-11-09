package com.futspring.backend.domain.notification.dto;

import com.futspring.backend.domain.notification.NotificationCategory;
import jakarta.validation.constraints.NotNull;

/**
 * One category's e-mail and push choices. GET always returns the four categories with both values and the defaults
 * applied. In a PUT both are optional (null = unchanged), but at least one must be sent.
 */
public record NotificationPreferenceDTO(
        @NotNull(message = "Informe a categoria") NotificationCategory category,
        Boolean emailEnabled,
        Boolean pushEnabled) {
}
