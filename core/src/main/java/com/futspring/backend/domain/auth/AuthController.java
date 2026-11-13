package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.auth.dto.AuthResponseDTO;
import com.futspring.backend.domain.auth.dto.ForgotPasswordRequestDTO;
import com.futspring.backend.domain.auth.dto.LoginRequestDTO;
import com.futspring.backend.domain.auth.dto.RegisterRequestDTO;
import com.futspring.backend.domain.auth.dto.ResetPasswordRequestDTO;
import com.futspring.backend.domain.auth.passwordreset.PasswordResetService;
import com.futspring.backend.domain.auth.session.RefreshSessionService;
import com.futspring.backend.domain.auth.session.SessionCookie;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Every route here but logout is rate limited by AuthRateLimitFilter. Login, registration and refresh answer the access
// token in the body and the refresh token in the futspring_refresh cookie (SessionCookie)
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RefreshSessionService refreshSessionService;
    private final SessionCookie sessionCookie;
    private final com.futspring.backend.domain.auth.verification.EmailVerificationService verification;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequestDTO request) {
        authService.register(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verification-email")
    public ResponseEntity<Void> verificationEmail(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        verification.request(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody com.futspring.backend.domain.auth.dto.VerifyEmailRequestDTO request) {
        verification.verify(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return sessionCookie.respond(HttpStatus.OK, authService.login(request));
    }

    // Reads only the cookie (JwtAuthFilter ignores an expired Authorization header here); a session that ended → 401
    // deleting the cookie
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = SessionCookie.NAME, required = false) String refreshToken,
                                     @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        return refreshSessionService.refresh(refreshToken, origin)
                .<ResponseEntity<?>>map(session -> sessionCookie.respond(HttpStatus.OK, session))
                .orElseGet(sessionCookie::expired);
    }

    // Ends this browser's session; idempotent
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = SessionCookie.NAME, required = false) String refreshToken) {
        refreshSessionService.close(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessionCookie.cleared()).build();
    }

    // Always 204, whether or not the e-mail has an account
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
