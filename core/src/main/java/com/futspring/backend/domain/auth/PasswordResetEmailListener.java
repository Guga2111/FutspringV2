package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.mail.EmailSender;
import com.futspring.backend.domain.mail.MailConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Sends the reset e-mail only after the token is committed (no e-mail for a rolled-back token) and on
 * another thread, so the response time doesn't depend on Resend nor reveal whether the account exists.
 * A failure is logged by Spring's async exception handler; the user can ask again.
 */
@Component
@RequiredArgsConstructor
public class PasswordResetEmailListener {

    private final EmailSender emailSender;
    private final MailConfig mailConfig;

    @Async
    @TransactionalEventListener
    public void onResetRequested(PasswordResetRequestedEvent event) {
        String appUrl = mailConfig.getFrontendUrl();
        String link = UriComponentsBuilder.fromHttpUrl(appUrl)
                .path("/reset-password")
                .queryParam("token", event.rawToken())
                .toUriString();
        String html = PasswordResetEmail.html(appUrl, event.username(), link);
        emailSender.send(event.email(), PasswordResetEmail.SUBJECT, html, List.of(PasswordResetEmail.LOGO));
    }
}
