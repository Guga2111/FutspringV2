package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEmailContentTest {

    private final User leal = User.builder().id(1L).email("leal@example.com").username("Leal").password("hash").build();
    private final User member = User.builder().id(2L).email("m@example.com").username("member").password("hash").build();
    private final Pelada pelada = Pelada.builder().id(10L).name("Pelada do Fut").timeZone("America/Sao_Paulo").build();
    private final Daily daily = Daily.builder().id(100L).pelada(pelada)
            .dailyDate(LocalDate.of(2026, 10, 31)).dailyTime("20:00").build();

    private Notification notification(NotificationType type, NotificationAward award) {
        boolean session = type != NotificationType.ADDED_TO_PELADA && type != NotificationType.REMOVED_FROM_PELADA
                && type.category() != NotificationCategory.MEMBERSHIP;
        return Notification.builder().id(5L).type(type).recipient(member).actor(leal).pelada(pelada)
                .daily(session ? daily : null).award(award).createdAt(Instant.now()).build();
    }

    // Every type has text (the switch is exhaustive); the emailable ones are the ones that reach an inbox
    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void everyTypeRendersSubjectTitleBodyAndCta(NotificationType type) {
        NotificationAward award = type == NotificationType.AWARD_WON ? NotificationAward.ARTILHEIRO : null;

        NotificationEmailContent content = NotificationEmailContent.of(notification(type, award));

        assertThat(content.subject()).isNotBlank().doesNotContain("null");
        assertThat(content.title()).isNotBlank().doesNotContain("null");
        assertThat(content.body()).isNotBlank().doesNotContain("null");
        assertThat(content.ctaLabel()).isNotBlank();
        assertThat(content.ctaPath()).startsWith("/");
    }

    @Test
    void everyAwardHasItsOwnName() {
        Arrays.stream(NotificationAward.values()).forEach(award -> {
            NotificationEmailContent content = NotificationEmailContent.of(notification(NotificationType.AWARD_WON, award));
            assertThat(content.subject()).startsWith(award.label()).contains("sáb, 31/10");
            assertThat(content.body()).contains(award.label());
        });
        assertThat(NotificationAward.WILTBALL.label()).isEqualTo("Bola Murcha");
    }

    @Test
    void sessionTypesUseTheSessionDateAndLinkToIt() {
        NotificationEmailContent bumped = NotificationEmailContent.of(notification(NotificationType.DIARISTA_BUMPED, null));

        assertThat(bumped.subject()).isEqualTo("Você perdeu a vaga na Pelada do Fut");
        assertThat(bumped.body()).contains("Leal").contains("sáb, 31/10 às 20:00");
        assertThat(bumped.ctaPath()).isEqualTo("/daily/100");
    }

    @Test
    void membershipTypesLinkToThePelada_removedLinksHome() {
        assertThat(NotificationEmailContent.of(notification(NotificationType.ADDED_TO_PELADA, null)).ctaPath())
                .isEqualTo("/pelada/10");
        assertThat(NotificationEmailContent.of(notification(NotificationType.REMOVED_FROM_PELADA, null)).ctaPath())
                .isEqualTo("/home");
    }

    @Test
    void systemEventWithoutActor_stillReadsWell() {
        Notification n = notification(NotificationType.ADDED_TO_PELADA, null);
        n.setActor(null);

        assertThat(NotificationEmailContent.of(n).body()).isEqualTo("Você entrou na Pelada do Fut.");
    }

    @Test
    void html_fillsEveryPlaceholderAndEscapesUserText() {
        pelada.setName("<b>Fut</b> {{unsubscribeUrl}}");
        NotificationEmailContent content = NotificationEmailContent.of(notification(NotificationType.DIARISTA_BUMPED, null));

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
