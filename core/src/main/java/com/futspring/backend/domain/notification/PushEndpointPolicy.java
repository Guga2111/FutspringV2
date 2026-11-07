package com.futspring.backend.domain.notification;

import com.futspring.backend.shared.exception.AppException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * What a Web Push subscription may contain. The server POSTs to the endpoint the client sends, so it must be an
 * https URL on a known push service (anti-SSRF): no other host, no IP literal, no port but 443, no credentials. The
 * keys must decode (base64url) to a P-256 uncompressed point (65 bytes, 0x04 first) and a 16-byte auth secret.
 */
final class PushEndpointPolicy {

    // Exact hosts: Chrome/Android (FCM), Firefox (Mozilla autopush)
    private static final Set<String> HOSTS = Set.of(
            "fcm.googleapis.com", "android.googleapis.com", "updates.push.services.mozilla.com");

    // Any subdomain of: Safari (web.push.apple.com), Edge (*.notify.windows.com). The leading dot matters:
    // "push.apple.com.evil.com" doesn't end with ".push.apple.com"
    private static final Set<String> HOST_SUFFIXES = Set.of(".push.apple.com", ".notify.windows.com");

    static final int P256DH_BYTES = 65;
    static final int AUTH_BYTES = 16;

    private PushEndpointPolicy() {
    }

    static void requireValid(String endpoint, String p256dh, String auth) {
        if (!allowedEndpoint(endpoint)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Endpoint de notificação inválido");
        }
        byte[] key = decode(p256dh);
        byte[] secret = decode(auth);
        if (key == null || key.length != P256DH_BYTES || key[0] != 0x04 || secret == null || secret.length != AUTH_BYTES) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chaves de notificação inválidas");
        }
    }

    static boolean allowedEndpoint(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException | NullPointerException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getHost() == null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") || host.matches("[0-9.]+")) {
            return false;
        }
        return HOSTS.contains(host) || HOST_SUFFIXES.stream().anyMatch(host::endsWith);
    }

    private static byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
