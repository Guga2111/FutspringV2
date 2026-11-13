package com.futspring.backend.domain.auth.verification;

import com.futspring.backend.domain.mail.EmailSender;
import com.futspring.backend.domain.mail.MailConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class EmailVerificationListener {
    private final EmailSender sender;
    private final MailConfig config;

    @Async
    @TransactionalEventListener
    public void send(EmailVerificationRequested event) {
        String link = UriComponentsBuilder.fromUriString(config.getFrontendUrl()).path("/verify-email")
                .queryParam("token", event.token()).toUriString();
        String html = "<html lang=\"pt-BR\"><body><h1>Verifique seu e-mail</h1><p>Olá, "
                + HtmlUtils.htmlEscape(event.username()) + ".</p><p>Confirme seu e-mail para entrar no Futspring. O link vale por 30 minutos.</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">Verificar e-mail</a></p>"
                + "<p>Você precisará informar a senha da sua conta. Se não pediu este e-mail, ignore esta mensagem.</p></body></html>";
        sender.send(event.email(), "Verifique seu e-mail — Futspring", html, java.util.List.of());
    }
}
