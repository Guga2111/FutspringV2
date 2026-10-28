package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.daily.AttendanceWindow;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.futspring.backend.domain.notification.NotificationType.DIARISTA_WINDOW_OPEN;
import static com.futspring.backend.domain.notification.NotificationType.SESSION_REMINDER;

/**
 * Every 15 min: for each upcoming session whose diarista window has opened (start − 24 h in the pelada's zone)
 * and that hasn't started, reminds the mensalistas who haven't confirmed and tells the diaristas who haven't
 * confirmed that they can, while there are spots left. Any session in the window is picked, not only the ones
 * that opened since the last run, so nothing is lost while the API is down; the recipient queries skip whoever
 * was already notified, so a session is notified once per recipient and type.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledNotificationJob {

    private static final Set<DailyStatus> OPEN = EnumSet.of(DailyStatus.SCHEDULED, DailyStatus.CONFIRMED);

    private final DailyRepository dailyRepository;
    private final DailyAttendanceRepository attendanceRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Scheduled(cron = "0 */15 * * * *")
    public void run() {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        // Coarse range: a session starting in the next 24 h falls in it in any zone; the exact window is per session
        Map<Pelada, List<Daily>> byPelada = dailyRepository
                .findWithPeladaByStatusInAndDateBetween(OPEN, today.minusDays(1), today.plusDays(2)).stream()
                .collect(Collectors.groupingBy(Daily::getPelada, LinkedHashMap::new, Collectors.toList()));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // One transaction per pelada: a failing pelada rolls back only its own notifications
        byPelada.forEach((pelada, dailies) -> {
            try {
                List<Daily> due = dailies.stream().filter(d -> isDue(d, now)).toList();
                if (!due.isEmpty()) {
                    transaction.executeWithoutResult(status -> due.forEach(this::notifySession));
                }
            } catch (RuntimeException e) {
                log.error("Scheduled notifications failed for pelada {} (timeZone={})", pelada.getId(),
                        pelada.getTimeZone(), e);
            }
        });
    }

    private static boolean isDue(Daily daily, Instant now) {
        Instant opensAt = AttendanceWindow.diaristaOpensAt(daily);
        if (now.isBefore(opensAt) || !now.isBefore(AttendanceWindow.startsAt(daily))) {
            return false;
        }
        // Created after the window opened: SESSION_CREATED already told everyone and confirmations were open from
        // the start. createdAt is a server-local timestamp, so it is read in the JVM zone that wrote it
        return daily.getCreatedAt() == null
                || daily.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().isBefore(opensAt);
    }

    private void notifySession(Daily daily) {
        notificationService.notifyScheduled(SESSION_REMINDER, daily,
                notificationRepository.findReminderRecipients(daily));
        if (attendanceRepository.countByDaily(daily) < daily.getPelada().capacity()) {
            notificationService.notifyScheduled(DIARISTA_WINDOW_OPEN, daily,
                    notificationRepository.findWindowOpenRecipients(daily));
        }
    }
}
