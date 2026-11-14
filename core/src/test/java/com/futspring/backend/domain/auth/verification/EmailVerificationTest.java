package com.futspring.backend.domain.auth.verification;

import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.domain.user.*;
import com.futspring.backend.domain.auth.*;
import com.futspring.backend.domain.auth.session.RefreshSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.http.MediaType;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RecordApplicationEvents
class EmailVerificationTest extends BaseIntegrationTest {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired EmailVerificationService verification;
    @Autowired AccessTokenVerifier verifier;
    @Autowired RefreshSessionService sessions;
    @Autowired ApplicationEvents events;
    User user;
    @BeforeEach void prepare() {
        user = users.save(User.builder().username("Player").email("player@test.com").password(passwords.encode("password123")).build());
    }
    private String token() {
        verification.request(user.getEmail());
        return events.stream(EmailVerificationRequested.class).reduce((a,b)->b).orElseThrow().token();
    }
    @Test void existingUnverifiedAccountsCannotLoginOrUseOldTokensOrOpenSessions() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"player@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(verifier.verify(generateToken(user.getId(), user.getEmail()))).isEmpty();
        assertThatThrownBy(() -> sessions.open(user)).isInstanceOf(com.futspring.backend.shared.exception.AppException.class);
    }
    @Test void tokenIsHashedPasswordBoundSingleUseAndVerificationRequiresAnExplicitPost() throws Exception {
        String token = token();
        assertThat(user.getEmailVerificationHash()).isEqualTo(SecretTokens.hash(token)).isNotEqualTo(token);
        assertThatThrownBy(() -> verification.verify(token, "wrong-password")).isInstanceOf(com.futspring.backend.shared.exception.AppException.class);
        assertThat(user.getEmailVerifiedAt()).isNull();
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token",token)).andExpect(status().isMethodNotAllowed());
        String old = generateToken(user.getId(),user.getEmail());
        mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.futspring.backend.domain.auth.dto.VerifyEmailRequestDTO(token,"password123"))))
                .andExpect(status().isNoContent());
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(user.getEmailVerificationHash()).isNull();
        assertThat(verifier.verify(old)).isEmpty();
        assertThatThrownBy(() -> verification.verify(token,"password123")).isInstanceOf(com.futspring.backend.shared.exception.AppException.class);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"player@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
    }
    @Test void resendIsThrottledReplacesOldTokenAndExpiredLinksCannotVerify() {
        String first=token();
        verification.request(user.getEmail());
        assertThat(events.stream(EmailVerificationRequested.class).count()).isEqualTo(1);
        user.setEmailVerificationSentAt(Instant.now().minusSeconds(61));
        String second=token();
        assertThat(second).isNotEqualTo(first);
        assertThatThrownBy(() -> verification.verify(first,"password123")).isInstanceOf(com.futspring.backend.shared.exception.AppException.class);
        user.setEmailVerificationExpiresAt(Instant.now());
        assertThatThrownBy(() -> verification.verify(second,"password123")).isInstanceOf(com.futspring.backend.shared.exception.AppException.class);
    }
    @Test void resendDoesNotDiscloseUnknownOrAlreadyVerifiedAccounts() throws Exception {
        user.setEmailVerifiedAt(Instant.now());
        for(String email:java.util.List.of(user.getEmail(),"unknown@test.com")) {
            mockMvc.perform(post("/api/v1/auth/verification-email").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\""+email+"\"}"))
                    .andExpect(status().isNoContent());
        }
        assertThat(events.stream(EmailVerificationRequested.class)).isEmpty();
    }
}
