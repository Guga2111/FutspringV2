package com.futspring.backend.domain.pelada;

import com.futspring.backend.shared.config.JwtConfig;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/** Signed invite-link record id; expiry and revocation remain authoritative in the database. */
@Component
public class InviteLinkTokens {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String CONTEXT = "pelada-invite-link";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();



    private final SecretKeySpec key;

    public InviteLinkTokens(JwtConfig jwtConfig) {
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8),
                ALGORITHM), CONTEXT.getBytes(StandardCharsets.UTF_8)), ALGORITHM);
    }

    public String create(Long id) {
        byte[] payload = id.toString().getBytes(StandardCharsets.UTF_8);
        return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(hmac(key, payload));
    }

    public Optional<Long> verify(String token) {
        if (token == null || token.length() > 200) return Optional.empty();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) return Optional.empty();
        try {
            byte[] payload = DECODER.decode(parts[0]);
            if (!MessageDigest.isEqual(hmac(key, payload), DECODER.decode(parts[1]))) return Optional.empty();
            long id = Long.parseLong(new String(payload, StandardCharsets.UTF_8));
            // Reject alternate encodings of the same bearer token.
            return id > 0 && create(id).equals(token) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException e) {
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
