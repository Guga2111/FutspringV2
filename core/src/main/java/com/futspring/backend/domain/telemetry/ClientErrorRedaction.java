package com.futspring.backend.domain.telemetry;

import java.util.regex.Pattern;

/** Untrusted browser telemetry must never put link credentials or authorization values in logs. */
final class ClientErrorRedaction {
    private static final Pattern LINK = Pattern.compile("(?i)(/|%2f)(join|invite-links)(/|%2f)[^\\s/?#\\\"<>]+");
    private static final Pattern QUERY = Pattern.compile("[?#][^\\s)\\\"<>]*");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=%-]+");
    private static final Pattern SECRET = Pattern.compile("(?i)([\\\"']?(?:token|refreshToken|password|authorization|cookie)[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s,;\\\"'}]+");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private ClientErrorRedaction() { }
    static String redact(String value) {
        if (value == null) return null;
        String safe = LINK.matcher(value).replaceAll("/$2/[REDACTED]");
        safe = QUERY.matcher(safe).replaceAll("");
        safe = BEARER.matcher(safe).replaceAll("Bearer [REDACTED]");
        safe = SECRET.matcher(safe).replaceAll("$1[REDACTED]");
        return JWT.matcher(safe).replaceAll("[REDACTED]");
    }
}
