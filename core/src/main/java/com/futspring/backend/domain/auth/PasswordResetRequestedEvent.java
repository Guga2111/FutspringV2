package com.futspring.backend.domain.auth;

// Published by PasswordResetService, handled after the commit by PasswordResetEmailListener
public record PasswordResetRequestedEvent(String email, String username, String rawToken) {

    // Keeps the token out of logs
    @Override
    public String toString() {
        return "PasswordResetRequestedEvent[email=" + email + "]";
    }
}
