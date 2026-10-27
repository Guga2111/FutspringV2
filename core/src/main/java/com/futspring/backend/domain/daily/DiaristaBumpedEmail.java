package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.mail.InlineImage;
import lombok.SneakyThrows;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

// pt-BR "you lost your spot" e-mail to a bumped diarista. The HTML and the logo live in resources/email
final class DiaristaBumpedEmail {

    static final String SUBJECT = "Você perdeu a vaga na pelada — Futspring";

    // Sent inside the e-mail and referenced as cid:futspring-logo in the template
    static final InlineImage LOGO = new InlineImage(
            "futspring-logo", "futspring-logo.png", "image/png", loadBytes("email/futspring-logo.png"));

    private static final String TEMPLATE = new String(loadBytes("email/diarista-bumped.html"), StandardCharsets.UTF_8);

    private DiaristaBumpedEmail() {
    }

    // Text typed by users is replaced last, so it can never fill another placeholder
    static String html(String appUrl, String link, String sessionDate, String username, String bumpedBy, String peladaName) {
        return TEMPLATE
                .replace("{{appUrl}}", appUrl)
                .replace("{{link}}", link)
                .replace("{{sessionDate}}", sessionDate)
                .replace("{{peladaName}}", HtmlUtils.htmlEscape(peladaName))
                .replace("{{bumpedBy}}", HtmlUtils.htmlEscape(bumpedBy))
                .replace("{{username}}", HtmlUtils.htmlEscape(username));
    }

    // Both files ship inside the jar, so a missing one is a build error, not a runtime case
    @SneakyThrows
    private static byte[] loadBytes(String path) {
        return new ClassPathResource(path).getContentAsByteArray();
    }
}
