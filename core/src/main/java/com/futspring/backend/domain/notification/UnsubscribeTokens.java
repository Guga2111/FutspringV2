package com.futspring.backend.domain.notification;

import com.futspring.backend.shared.config.JwtConfig;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * The one-click unsubscribe token of a user and a category: base64url("userId:CATEGORY") + "." + HMAC-SHA256 of it.
 * The key is derived from JWT_SECRET with its own context, so it is never the JWT key itself. Not stored and no
 * expiry: it only turns that one category of that one user off or on.
 */
@Component
public class UnsubscribeTokens {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String CONTEXT = "notification-unsubscribe";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public record Target(Long userId, NotificationCategory category) { }

    private final SecretKeySpec key;

    public UnsubscribeTokens(JwtConfig jwtConfig) {
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8),
                ALGORITHM), CONTEXT.getBytes(StandardCharsets.UTF_8)), ALGORITHM);
    }

    public String create(Long userId, NotificationCategory category) {
        byte[] payload = (userId + ":" + category.name()).getBytes(StandardCharsets.UTF_8);
        return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(hmac(key, payload));
    }

    // Empty for anything that isn't a token this server signed (bad format, tampered, unknown category)
    public Optional<Target> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            byte[] payload = DECODER.decode(parts[0]);
            if (!MessageDigest.isEqual(hmac(key, payload), DECODER.decode(parts[1]))) {
                return Optional.empty();
            }
            String[] fields = new String(payload, StandardCharsets.UTF_8).split(":", -1);
            if (fields.length != 2) {
                return Optional.empty();
            }
            return Optional.of(new Target(Long.parseLong(fields[0]), NotificationCategory.valueOf(fields[1])));
        } catch (IllegalArgumentException e) {
            // bad base64, number or category name
            return Optional.empty();
        }
    }

    // HmacSHA256 is guaranteed on every JVM, so the checked exceptions can't happen
    @SneakyThrows
    private static byte[] hmac(SecretKeySpec key, byte[] data) {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(key);
        return mac.doFinal(data);
    }
}
