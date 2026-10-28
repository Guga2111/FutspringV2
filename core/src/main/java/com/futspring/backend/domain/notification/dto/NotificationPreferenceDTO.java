package com.futspring.backend.domain.notification.dto;

import com.futspring.backend.domain.notification.NotificationCategory;
import jakarta.validation.constraints.NotNull;

// One category's e-mail choice; GET always returns the four categories with the defaults applied
public record NotificationPreferenceDTO(
        @NotNull(message = "Informe a categoria") NotificationCategory category,
        @NotNull(message = "Informe se o e-mail fica ligado") Boolean emailEnabled) {
}
