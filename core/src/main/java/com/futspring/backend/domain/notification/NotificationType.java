package com.futspring.backend.domain.notification;

import static com.futspring.backend.domain.notification.NotificationCategory.*;

/**
 * Every kind of notification, with its category and whether it may go by e-mail (some stay in-app even when the
 * category is on). Adding a value means updating the CHECK constraint of notifications.type.
 */
public enum NotificationType {
    DIARISTA_BUMPED(ATTENDANCE, true),
    SESSION_CANCELED(ATTENDANCE, true),
    ATTENDANCE_REMOVED_BY_ADMIN(ATTENDANCE, true),
    ATTENDANCE_CONFIRMED_BY_ADMIN(ATTENDANCE, false),
    SESSION_REMINDER(ATTENDANCE, true),

    SESSION_CREATED(SESSION, true),
    DIARISTA_WINDOW_OPEN(SESSION, true),
    TEAMS_SORTED(SESSION, false),
    SESSION_FINISHED(SESSION, true),

    ADDED_TO_PELADA(MEMBERSHIP, true),
    REMOVED_FROM_PELADA(MEMBERSHIP, true),
    ADMIN_GRANTED(MEMBERSHIP, true),
    ADMIN_REVOKED(MEMBERSHIP, false),
    MENSALISTA_GRANTED(MEMBERSHIP, true),
    MENSALISTA_REVOKED(MEMBERSHIP, true),
    PELADA_TRANSFERRED(MEMBERSHIP, true),

    AWARD_WON(AWARDS, true);

    private final NotificationCategory category;
    private final boolean emailable;

    NotificationType(NotificationCategory category, boolean emailable) {
        this.category = category;
        this.emailable = emailable;
    }

    public NotificationCategory category() {
        return category;
    }

    public boolean emailable() {
        return emailable;
    }
}
