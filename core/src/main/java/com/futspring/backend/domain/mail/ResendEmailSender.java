package com.futspring.backend.domain.mail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;

// POST https://api.resend.com/emails. Errors (4xx/5xx, timeouts) are thrown as RestClientException
@RequiredArgsConstructor
public class ResendEmailSender implements EmailSender {

    private final RestClient restClient;
    private final String from;

    @Override
    public void send(String to, String subject, String html, List<InlineImage> inlineImages) {
        List<ResendAttachment> attachments = inlineImages.stream().map(ResendAttachment::from).toList();
        restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResendEmailRequest(from, List.of(to), subject, html, attachments))
                .retrieve()
                .toBodilessEntity();
    }

    record ResendEmailRequest(String from, List<String> to, String subject, String html,
                              List<ResendAttachment> attachments) {
    }

    // Resend inline image: base64 content + content_id (the cid: in the HTML)
    record ResendAttachment(String content,
                            String filename,
                            @JsonProperty("content_id") String contentId,
                            @JsonProperty("content_type") String contentType) {

        static ResendAttachment from(InlineImage image) {
            return new ResendAttachment(Base64.getEncoder().encodeToString(image.content()),
                    image.filename(), image.contentId(), image.contentType());
        }
    }
}
