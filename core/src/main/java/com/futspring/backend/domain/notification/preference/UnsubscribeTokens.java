package com.futspring.backend.domain.notification.preference;

import com.futspring.backend.domain.notification.NotificationCategory;
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
 * Expiring unsubscribe capability for one user/category, HMAC-SHA256 signed with a context-derived key.
 * Payload: userId:category:preferenceVersion:expiresAt. Legacy permanent tokens are rejected.
 */
@Component
public class UnsubscribeTokens {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String CONTEXT = "notification-unsubscribe";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public record Target(Long userId, NotificationCategory category, int version) { }

    private final SecretKeySpec key;
    private final java.time.Clock clock;

    public UnsubscribeTokens(JwtConfig jwtConfig, java.time.Clock clock) {
        this.clock = clock;
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8),
                ALGORITHM), CONTEXT.getBytes(StandardCharsets.UTF_8)), ALGORITHM);
    }

    public String create(Long userId, NotificationCategory category, int version) {
        long expires = clock.instant().plus(java.time.Duration.ofDays(30)).getEpochSecond();
        byte[] payload = (userId + ":" + category.name() + ":" + version + ":" + expires).getBytes(StandardCharsets.UTF_8);
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
            byte[] signature = DECODER.decode(parts[1]);
            // Reject alternative Base64 spellings whose unused padding bits decode to the same bytes.
            if (!ENCODER.encodeToString(payload).equals(parts[0]) || !ENCODER.encodeToString(signature).equals(parts[1])
                    || !MessageDigest.isEqual(hmac(key, payload), signature)) {
                return Optional.empty();
            }
            String[] fields = new String(payload, StandardCharsets.UTF_8).split(":", -1);
            if (fields.length != 4) {
                return Optional.empty();
            }
            if (clock.instant().getEpochSecond() >= Long.parseLong(fields[3])) return Optional.empty();
            return Optional.of(new Target(Long.parseLong(fields[0]), NotificationCategory.valueOf(fields[1]), Integer.parseInt(fields[2])));
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
