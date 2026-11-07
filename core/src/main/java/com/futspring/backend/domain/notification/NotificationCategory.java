package com.futspring.backend.domain.notification;

/** Groups the notification types; the e-mail and push preferences are chosen per category. */
public enum NotificationCategory {
    SESSION(false, true, "Sessões"),
    ATTENDANCE(true, true, "Presença"),
    MEMBERSHIP(true, true, "Membros"),
    AWARDS(false, true, "Prêmios");

    private final boolean defaultEmail;
    private final boolean defaultPush;
    private final String label;

    NotificationCategory(boolean defaultEmail, boolean defaultPush, String label) {
        this.defaultEmail = defaultEmail;
        this.defaultPush = defaultPush;
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

    // Push is on in every category: the user already opted in by enabling it on the device. Applies while
    // notification_preferences.push_enabled is null (no row, or a row that only stores the e-mail choice)
    public boolean defaultPush() {
        return defaultPush;
    }
}
