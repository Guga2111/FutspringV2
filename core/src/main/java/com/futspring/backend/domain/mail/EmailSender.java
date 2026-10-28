package com.futspring.backend.domain.mail;

import java.util.List;
import java.util.Map;

// Sends transactional e-mails. The implementation is chosen in MailConfig (Resend, or a no-op without an API key)
public interface EmailSender {

    // Extra headers, e.g. List-Unsubscribe on notification e-mails
    void send(String to, String subject, String html, List<InlineImage> inlineImages, Map<String, String> headers);

    default void send(String to, String subject, String html, List<InlineImage> inlineImages) {
        send(to, subject, html, inlineImages, Map.of());
    }

    // False for the no-op sender, so callers can skip building e-mails nobody will send
    default boolean enabled() {
        return true;
    }
}
