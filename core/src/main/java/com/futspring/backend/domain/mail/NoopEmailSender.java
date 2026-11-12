package com.futspring.backend.domain.mail;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

// Used when RESEND_API_KEY is empty (tests, local runs without a key). Logs neither the recipient nor the
// subject nor the body: they carry e-mails, names and password reset links
@Slf4j
public class NoopEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String html, List<InlineImage> inlineImages,
                     Map<String, String> headers) {
        log.warn("E-mail not sent (RESEND_API_KEY is not set)");
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
