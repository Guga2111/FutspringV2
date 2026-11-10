package com.futspring.backend.domain.notification.email;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.notification.Notification;
import com.futspring.backend.domain.notification.NotificationAward;
import com.futspring.backend.domain.notification.NotificationCategory;
import com.futspring.backend.domain.notification.NotificationContent;
import com.futspring.backend.domain.notification.NotificationType;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEmailTest {

    private final User leal = User.builder().id(1L).email("leal@example.com").username("Leal").password("hash").build();
    private final User member = User.builder().id(2L).email("m@example.com").username("member").password("hash").build();
    private final Pelada pelada = Pelada.builder().id(10L).name("Pelada do Fut").timeZone("America/Sao_Paulo").build();
    private final Daily daily = Daily.builder().id(100L).pelada(pelada)
            .dailyDate(LocalDate.of(2026, 10, 31)).dailyTime("20:00").build();

    private Notification notification(NotificationType type, NotificationAward award) {
        boolean session = type != NotificationType.ADDED_TO_PELADA && type != NotificationType.REMOVED_FROM_PELADA
                && type.category() != NotificationCategory.MEMBERSHIP && type.category() != NotificationCategory.BILLING;
        // the paid period ends 07/12/2026 23:59:59 in São Paulo
        Instant validUntil = type.category() == NotificationCategory.BILLING
                ? Instant.parse("2026-12-08T02:59:59Z") : null;
        return Notification.builder().id(5L).type(type).recipient(member).actor(leal).pelada(pelada)
                .daily(session ? daily : null).award(award).validUntil(validUntil).createdAt(Instant.now()).build();
    }

    @Test
    void html_fillsEveryPlaceholderAndEscapesUserText() {
        pelada.setName("<b>Fut</b> {{unsubscribeUrl}}");
        NotificationContent content = NotificationContent.of(notification(NotificationType.DIARISTA_BUMPED, null));

        String html = NotificationEmail.html(content, NotificationCategory.ATTENDANCE, "https://app.example.com",
                "https://app.example.com/daily/100", "https://app.example.com/settings/notifications",
                "https://app.example.com/notifications/unsubscribe?token=a.b");

        assertThat(html).doesNotContain("{{title}}", "{{body}}", "{{ctaUrl}}", "{{ctaLabel}}", "{{categoryLabel}}",
                "{{preferencesUrl}}", "{{appUrl}}", "<b>Fut</b>");
        assertThat(html).contains("&lt;b&gt;Fut&lt;/b&gt; {{unsubscribeUrl}}")
                .contains("Você perdeu a vaga")
                .contains("href=\"https://app.example.com/daily/100\"")
                .contains("href=\"https://app.example.com/notifications/unsubscribe?token=a.b\"")
                .contains("os e-mails de <strong style=\"color:#0F2E1A\">Presença</strong> estão ligados");
    }
}
