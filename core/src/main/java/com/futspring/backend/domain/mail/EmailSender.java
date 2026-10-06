package com.futspring.backend.domain.mail;

import java.util.List;

// Sends transactional e-mails. The implementation is chosen in MailConfig (Resend, or a no-op without an API key)
public interface EmailSender {

    void send(String to, String subject, String html, List<InlineImage> inlineImages);
}
