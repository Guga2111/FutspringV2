package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.mail.EmailSender;
import com.futspring.backend.domain.mail.MailConfig;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiaristaBumpedEmailListenerTest {

    @Mock DailyAttendanceBumpRepository bumpRepository;
    @Mock EmailSender emailSender;
    @Mock MailConfig mailConfig;

    @Test
    void onDiaristaBumped_sendsTheSessionLinkAndEscapesUserText() {
        Pelada pelada = Pelada.builder().id(10L).name("Pelada <do> Fut").build();
        Daily daily = Daily.builder().id(77L).pelada(pelada).dailyDate(LocalDate.of(2026, 10, 30)).dailyTime("20:00").build();
        User diarista = User.builder().id(2L).email("d@example.com").username("<b>dani</b>").build();
        User mensalista = User.builder().id(3L).email("m@example.com").username("Pedro & Cia").build();
        DailyAttendanceBump bump = DailyAttendanceBump.builder().id(5L).daily(daily).user(diarista).bumpedBy(mensalista)
                .bumpedAt(Instant.parse("2026-10-30T12:00:00Z")).build();
        when(bumpRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(bump));
        when(mailConfig.getFrontendUrl()).thenReturn("https://futspring.example.com");

        new DiaristaBumpedEmailListener(bumpRepository, emailSender, mailConfig).onDiaristaBumped(new DiaristaBumpedEvent(5L));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("d@example.com"), eq(DiaristaBumpedEmail.SUBJECT), html.capture(),
                eq(List.of(DiaristaBumpedEmail.LOGO)));
        assertThat(html.getValue())
                .contains("href=\"https://futspring.example.com/daily/77\"")
                .contains("sex, 30/10 às 20:00")
                .contains("&lt;b&gt;dani&lt;/b&gt;")
                .contains("Pedro &amp; Cia")
                .contains("Pelada &lt;do&gt; Fut")
                .contains("src=\"cid:" + DiaristaBumpedEmail.LOGO.contentId() + "\"")
                .doesNotContain("<b>dani</b>")
                .doesNotContain("{{");
    }

    @Test
    void onDiaristaBumped_bumpGone_sendsNothing() {
        when(bumpRepository.findByIdWithDetails(5L)).thenReturn(Optional.empty());

        new DiaristaBumpedEmailListener(bumpRepository, emailSender, mailConfig).onDiaristaBumped(new DiaristaBumpedEvent(5L));

        verifyNoInteractions(emailSender);
    }
}
