package com.futspring.backend.domain.mail;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailSenderTest {

    @Test
    void send_postsTheResendPayloadWithInlineImages() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendEmailSender sender = new ResendEmailSender(builder.build(), "Futspring <no-reply@example.com>");
        byte[] png = "png-bytes".getBytes(StandardCharsets.UTF_8);

        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.from").value("Futspring <no-reply@example.com>"))
                .andExpect(jsonPath("$.to[0]").value("ana@example.com"))
                .andExpect(jsonPath("$.subject").value("Assunto"))
                .andExpect(jsonPath("$.html").value("<p>oi</p>"))
                .andExpect(jsonPath("$.attachments[0].content").value(Base64.getEncoder().encodeToString(png)))
                .andExpect(jsonPath("$.attachments[0].filename").value("logo.png"))
                .andExpect(jsonPath("$.attachments[0].content_id").value("logo"))
                .andExpect(jsonPath("$.attachments[0].content_type").value("image/png"))
                .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));

        sender.send("ana@example.com", "Assunto", "<p>oi</p>",
                List.of(new InlineImage("logo", "logo.png", "image/png", png)));

        server.verify();
    }
}
