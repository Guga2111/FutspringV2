package com.futspring.backend.domain.notification;

/** Groups the notification types; the e-mail preference is chosen per category. */
public enum NotificationCategory {
    SESSION(false, "Sessões"),
    ATTENDANCE(true, "Presença"),
    MEMBERSHIP(true, "Membros"),
    AWARDS(false, "Prêmios");

    private final boolean defaultEmail;
    private final String label;

    NotificationCategory(boolean defaultEmail, String label) {
        this.defaultEmail = defaultEmail;
        this.label = label;
    }

    // pt-BR name shown in the e-mail footer ("os e-mails de Presença estão ligados"), same as the client's
    public String label() {
        return label;
    }

    // Applies to users without a notification_preferences row for the category
    public boolean defaultEmail() {
        return defaultEmail;
    }
}
