package com.futspring.backend.domain.mail;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

// Used when RESEND_API_KEY is empty (tests, local runs without a key). Logs only the recipient and the
// subject: the body may contain a password reset link
@Slf4j
public class NoopEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String html, List<InlineImage> inlineImages) {
        log.warn("E-mail not sent (RESEND_API_KEY is not set): to={} subject={}", to, subject);
    }
}
