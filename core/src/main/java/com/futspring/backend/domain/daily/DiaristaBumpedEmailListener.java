package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.DailyAttendanceBump;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.mail.EmailSender;
import com.futspring.backend.domain.mail.MailConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Tells a bumped diarista by e-mail, only after the confirmation commits and on another thread, so the
 * mensalista's confirmation never waits for Resend nor fails because of it. A failure is only logged by
 * Spring's async exception handler; the in-app alert is still shown.
 */
@Component
@RequiredArgsConstructor
public class DiaristaBumpedEmailListener {

    private final DailyAttendanceBumpRepository bumpRepository;
    private final EmailSender emailSender;
    private final MailConfig mailConfig;

    @Async
    @TransactionalEventListener
    public void onDiaristaBumped(DiaristaBumpedEvent event) {
        bumpRepository.findByIdWithDetails(event.bumpId()).ifPresent(this::send);
    }

    private void send(DailyAttendanceBump bump) {
        String appUrl = mailConfig.getFrontendUrl();
        String link = UriComponentsBuilder.fromHttpUrl(appUrl)
                .path("/daily/{id}")
                .buildAndExpand(bump.getDaily().getId())
                .toUriString();
        String html = DiaristaBumpedEmail.html(appUrl, link, AttendanceWindow.formatStart(bump.getDaily()),
                bump.getUser().getUsername(), bump.getBumpedBy().getUsername(), bump.getDaily().getPelada().getName());
        emailSender.send(bump.getUser().getEmail(), DiaristaBumpedEmail.SUBJECT, html, List.of(DiaristaBumpedEmail.LOGO));
    }
}
