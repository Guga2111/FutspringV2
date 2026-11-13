package com.futspring.backend.domain.auth;

import lombok.SneakyThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Secrets handed to the client once (password reset link, refresh cookie): 256 random bits, of which only the
// SHA-256 (hex) is stored, so a database or Redis dump opens no session
public final class SecretTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private SecretTokens() {
    }

    // URL-safe, 43 characters
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 is guaranteed on every JVM, so NoSuchAlgorithmException can't happen
    @SneakyThrows
    public static String hash(String rawToken) {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
