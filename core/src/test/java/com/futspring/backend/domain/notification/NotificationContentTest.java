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

class NotificationContentTest {

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

    // Every type has text (the switch is exhaustive); the emailable ones are the ones that reach an inbox
    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void everyTypeRendersSubjectTitleBodyAndCta(NotificationType type) {
        NotificationAward award = type == NotificationType.AWARD_WON ? NotificationAward.ARTILHEIRO : null;

        NotificationContent content = NotificationContent.of(notification(type, award));

        assertThat(content.subject()).isNotBlank().doesNotContain("null");
        assertThat(content.title()).isNotBlank().doesNotContain("null");
        assertThat(content.body()).isNotBlank().doesNotContain("null");
        assertThat(content.ctaLabel()).isNotBlank();
        assertThat(content.ctaPath()).startsWith("/");
    }

    @Test
    void subscriptionEnding_showsTheDayInSaoPaulo_andLinksToTheSubscriptionArea() {
        NotificationContent ending = NotificationContent.of(notification(NotificationType.SUBSCRIPTION_ENDING, null));

        assertThat(ending.subject()).isEqualTo("A assinatura da Pelada do Fut vence em 07/12");
        assertThat(ending.body()).isEqualTo("O plano da Pelada do Fut vence em 07/12. Assine para a pelada não travar.");
        assertThat(ending.ctaPath()).isEqualTo("/pelada/10/assinatura");
    }

    @Test
    void subscriptionPastDue_showsWhenItLocks_sevenDaysLater() {
        NotificationContent pastDue = NotificationContent.of(notification(NotificationType.SUBSCRIPTION_PAST_DUE, null));

        assertThat(pastDue.subject()).isEqualTo("Pagamento pendente: Pelada do Fut");
        assertThat(pastDue.body())
                .isEqualTo("O plano da Pelada do Fut venceu em 07/12. Renove até 14/12 para a pelada não travar.");
        assertThat(pastDue.ctaLabel()).isEqualTo("Renovar");
        assertThat(pastDue.ctaPath()).isEqualTo("/pelada/10/assinatura");
    }

    @Test
    void pixClaimed_namesTheCreator_andLinksToThePanel() {
        NotificationContent claimed = NotificationContent.of(notification(NotificationType.SUBSCRIPTION_PIX_CLAIMED, null));

        assertThat(claimed.subject()).isEqualTo("Pix informado: Pelada do Fut");
        assertThat(claimed.body()).isEqualTo("Leal informou o Pix da assinatura da Pelada do Fut. Confira e registre o pagamento.");
        assertThat(claimed.ctaPath()).isEqualTo("/admin/peladas/10");
    }

    @Test
    void invitationLinksToTheDedicatedScreen() {
        Notification n = notification(NotificationType.PELADA_INVITATION, null);
        n.setInvitationId(42L);
        assertThat(NotificationContent.of(n).ctaPath()).isEqualTo("/invitations/42");
        assertThat(NotificationContent.of(n).body()).contains("7 dias");
    }

    @Test
    void everyAwardHasItsOwnName() {
        Arrays.stream(NotificationAward.values()).forEach(award -> {
            NotificationContent content = NotificationContent.of(notification(NotificationType.AWARD_WON, award));
            assertThat(content.subject()).startsWith(award.label()).contains("sáb, 31/10");
            assertThat(content.body()).contains(award.label());
        });
        assertThat(NotificationAward.WILTBALL.label()).isEqualTo("Bola Murcha");
    }

    @Test
    void sessionTypesUseTheSessionDateAndLinkToIt() {
        NotificationContent bumped = NotificationContent.of(notification(NotificationType.DIARISTA_BUMPED, null));

        assertThat(bumped.subject()).isEqualTo("Você perdeu a vaga na Pelada do Fut");
        assertThat(bumped.body()).contains("Leal").contains("sáb, 31/10 às 20:00");
        assertThat(bumped.ctaPath()).isEqualTo("/daily/100");
    }

    @Test
    void teamsSorted_neutralTextForSortAndManualTeams() {
        NotificationContent teams = NotificationContent.of(notification(NotificationType.TEAMS_SORTED, null));

        assertThat(teams.subject()).isEqualTo("Times definidos: sáb, 31/10");
        assertThat(teams.title()).isEqualTo("Times definidos");
        assertThat(teams.body()).isEqualTo("Os times da sessão de sáb, 31/10 às 20:00 da Pelada do Fut foram definidos.");
        assertThat(teams.ctaPath()).isEqualTo("/daily/100");
    }

    @Test
    void membershipTypesLinkToThePelada_removedLinksHome() {
        assertThat(NotificationContent.of(notification(NotificationType.ADDED_TO_PELADA, null)).ctaPath())
                .isEqualTo("/pelada/10");
        assertThat(NotificationContent.of(notification(NotificationType.REMOVED_FROM_PELADA, null)).ctaPath())
                .isEqualTo("/home");
    }

    @Test
    void systemEventWithoutActor_stillReadsWell() {
        Notification n = notification(NotificationType.ADDED_TO_PELADA, null);
        n.setActor(null);

        assertThat(NotificationContent.of(n).body()).isEqualTo("Você entrou na Pelada do Fut.");
    }

    @Test
    void pushBody_keepsAShortBody_andCutsALongOneAt180WithAnEllipsis() {
        NotificationContent shortOne = new NotificationContent("s", "t", "Curto.", "c", "/home");
        assertThat(shortOne.pushBody()).isEqualTo("Curto.");

        String exact = "a".repeat(NotificationContent.PUSH_BODY_MAX);
        assertThat(new NotificationContent("s", "t", exact, "c", "/home").pushBody()).isEqualTo(exact);

        String cut = new NotificationContent("s", "t", "b".repeat(400), "c", "/home").pushBody();
        assertThat(cut).hasSize(NotificationContent.PUSH_BODY_MAX).endsWith("…").startsWith("bbb");
    }

    @Test
    void pushBody_neverSplitsAnEmoji() {
        String body = "a".repeat(NotificationContent.PUSH_BODY_MAX - 2) + "🏆🏆🏆";
        String cut = new NotificationContent("s", "t", body, "c", "/home").pushBody();

        assertThat(cut).endsWith("…");
        // a lone surrogate doesn't survive UTF-8 (it becomes "?")
        assertThat(new String(cut.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(cut);
        assertThat(cut.length()).isLessThanOrEqualTo(NotificationContent.PUSH_BODY_MAX);
    }
}
