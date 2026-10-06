package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.mail.EmailSender;
import com.futspring.backend.domain.mail.MailConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetEmailListenerTest {

    @Mock
    EmailSender emailSender;

    @Mock
    MailConfig mailConfig;

    @Test
    void onResetRequested_sendsLinkToTheFrontendAndEscapesTheUsername() {
        when(mailConfig.getFrontendUrl()).thenReturn("https://futspring.example.com");
        PasswordResetEmailListener listener = new PasswordResetEmailListener(emailSender, mailConfig);

        listener.onResetRequested(new PasswordResetRequestedEvent("a@example.com", "<b>alice</b>", "tok_123-abc"));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("a@example.com"), eq(PasswordResetEmail.SUBJECT), html.capture(),
                eq(List.of(PasswordResetEmail.LOGO)));
        assertThat(html.getValue())
                .contains("href=\"https://futspring.example.com/reset-password?token=tok_123-abc\"")
                .contains("&lt;b&gt;alice&lt;/b&gt;")
                .contains("src=\"cid:" + PasswordResetEmail.LOGO.contentId() + "\"")
                .contains("expira em 30 minutos")
                .contains("https://github.com/Guga2111")
                .contains("https://www.linkedin.com/in/luisgustavosampaio/")
                .doesNotContain("<b>alice</b>")
                .doesNotContain("{{");
    }

    @Test
    void logo_isAPngShippedWithTheJar() {
        assertThat(PasswordResetEmail.LOGO.contentType()).isEqualTo("image/png");
        assertThat(PasswordResetEmail.LOGO.content()).isNotEmpty();
    }
}
