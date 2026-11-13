package com.futspring.backend.domain.auth.session;

import com.futspring.backend.domain.auth.dto.AuthResponseDTO;
import com.futspring.backend.shared.exception.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * The refresh token's cookie: futspring_refresh, HttpOnly (out of reach of the page's scripts), Secure
 * (app.auth.cookie-secure, false only in the dev compose), SameSite=Strict, sent only to the auth routes: Path = the path
 * of API_URL + /api/v1/auth (production /api/api/v1/auth, since nginx strips /api), Max-Age = the session's TTL.
 */
@Component
public class SessionCookie {

    public static final String NAME = "futspring_refresh";

    private final String path;
    private final boolean secure;

    public SessionCookie(@Value("${app.api-url}") String apiUrl,
                         @Value("${app.auth.cookie-secure:true}") boolean secure) {
        String basePath = URI.create(apiUrl).getPath();
        this.path = (basePath == null ? "" : basePath.replaceAll("/+$", "")) + "/api/v1/auth";
        this.secure = secure;
    }

    /** The session's access token in the body and, when it was stored, its refresh token in the cookie. */
    public ResponseEntity<AuthResponseDTO> respond(HttpStatus status, IssuedSession session) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (session.refreshToken() != null) {
            response.header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), session.refreshMaxAge()));
        }
        return response.body(session.auth());
    }

    /** 401 for a refresh token that no longer opens a session, deleting the cookie. */
    public ResponseEntity<ErrorResponse> expired() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cleared())
                .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(),
                        "Sessão expirada. Entre novamente.", Instant.now()));
    }

    /** A Set-Cookie value that deletes the cookie. */
    public String cleared() {
        return cookie("", Duration.ZERO);
    }

    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
