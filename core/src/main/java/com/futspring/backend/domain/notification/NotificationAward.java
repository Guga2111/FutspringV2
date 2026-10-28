package com.futspring.backend.domain.notification;

/** The award of an AWARD_WON notification, with its pt-BR name (WILTBALL is "Bola Murcha" in the UI). */
public enum NotificationAward {
    PUSKAS("Puskás"),
    WILTBALL("Bola Murcha"),
    ARTILHEIRO("Artilheiro"),
    GARCOM("Garçom");

    private final String label;

    NotificationAward(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
