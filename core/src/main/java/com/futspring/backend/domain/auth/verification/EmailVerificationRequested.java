package com.futspring.backend.domain.auth.verification;

public record EmailVerificationRequested(String email, String username, String token) {
    @Override public String toString() { return "EmailVerificationRequested[redacted]"; }
}
