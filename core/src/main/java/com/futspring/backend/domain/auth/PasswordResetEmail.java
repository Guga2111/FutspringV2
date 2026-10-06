package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.mail.InlineImage;
import lombok.SneakyThrows;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

// pt-BR password reset e-mail. The HTML and the logo live in resources/email and are loaded once
final class PasswordResetEmail {

    static final String SUBJECT = "Redefinição de senha — Futspring";

    // Sent inside the e-mail and referenced as cid:futspring-logo in the template
    static final InlineImage LOGO = new InlineImage(
            "futspring-logo", "futspring-logo.png", "image/png", loadBytes("email/futspring-logo.png"));

    private static final String TEMPLATE = new String(loadBytes("email/password-reset.html"), StandardCharsets.UTF_8);

    private PasswordResetEmail() {
    }

    // The username is replaced last, so text typed by a user can never fill another placeholder
    static String html(String appUrl, String username, String link) {
        return TEMPLATE
                .replace("{{appUrl}}", appUrl)
                .replace("{{link}}", link)
                .replace("{{ttlMinutes}}", String.valueOf(PasswordResetService.TOKEN_TTL.toMinutes()))
                .replace("{{username}}", HtmlUtils.htmlEscape(username));
    }

    // Both files ship inside the jar, so a missing one is a build error, not a runtime case
    @SneakyThrows
    private static byte[] loadBytes(String path) {
        return new ClassPathResource(path).getContentAsByteArray();
    }
}
