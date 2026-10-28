package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.mail.InlineImage;
import lombok.SneakyThrows;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Fills resources/email/notification.html, the one template of every notification e-mail
final class NotificationEmail {

    // Sent inside the e-mail and referenced as cid:futspring-logo in the template
    static final InlineImage LOGO = new InlineImage(
            "futspring-logo", "futspring-logo.png", "image/png", loadBytes("email/futspring-logo.png"));

    private static final String TEMPLATE = new String(loadBytes("email/notification.html"), StandardCharsets.UTF_8);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private NotificationEmail() {
    }

    /**
     * Every value is HTML-escaped and replaced in a single pass, so text typed by users (pelada and user names)
     * can never fill another placeholder.
     */
    static String html(NotificationEmailContent content, NotificationCategory category, String appUrl, String ctaUrl,
                       String preferencesUrl, String unsubscribeUrl) {
        Map<String, String> values = Map.of(
                "appUrl", appUrl,
                "title", content.title(),
                "body", content.body(),
                "ctaLabel", content.ctaLabel(),
                "ctaUrl", ctaUrl,
                "categoryLabel", category.label(),
                "preferencesUrl", preferencesUrl,
                "unsubscribeUrl", unsubscribeUrl);
        Matcher matcher = PLACEHOLDER.matcher(TEMPLATE);
        return matcher.replaceAll(match -> Matcher.quoteReplacement(
                HtmlUtils.htmlEscape(values.getOrDefault(match.group(1), match.group()), "UTF-8")));
    }

    // Both files ship inside the jar, so a missing one is a build error, not a runtime case
    @SneakyThrows
    private static byte[] loadBytes(String path) {
        return new ClassPathResource(path).getContentAsByteArray();
    }
}
