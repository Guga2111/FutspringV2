package com.futspring.backend.domain.notification;

/** Groups the notification types; the e-mail preference is chosen per category. */
public enum NotificationCategory {
    SESSION(false),
    ATTENDANCE(true),
    MEMBERSHIP(true),
    AWARDS(false);

    private final boolean defaultEmail;

    NotificationCategory(boolean defaultEmail) {
        this.defaultEmail = defaultEmail;
    }

    // Applies to users without a notification_preferences row for the category
    public boolean defaultEmail() {
        return defaultEmail;
    }
}
