package com.futspring.backend.domain.auth;

import lombok.SneakyThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Password reset tokens: 256 random bits sent in the link, SHA-256 (hex) stored in the database
final class ResetTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private ResetTokens() {
    }

    // URL-safe, 43 characters
    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 is guaranteed on every JVM, so NoSuchAlgorithmException can't happen
    @SneakyThrows
    static String hash(String rawToken) {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
