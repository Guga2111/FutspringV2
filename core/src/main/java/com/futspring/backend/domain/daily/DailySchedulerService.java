package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySchedulerService {

    private final PeladaRepository peladaRepository;
    private final DailyRepository dailyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    // Hourly: creates the next daily of each pelada once its day starts at most 24 h from now, in the pelada's zone.
    // One transaction per pelada: a failing pelada rolls back only its own daily and notifications
    @Scheduled(cron = "0 0 * * * *")
    public void autoCreateDailies() {
        List<Pelada> peladas = peladaRepository.findByAutoCreateDailyEnabledTrue();
        Instant now = Instant.now(clock);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        peladas.forEach(pelada -> {
            try {
                // Reloaded inside the transaction: the listed peladas are detached, and SESSION_CREATED reads the
                // lazy members
                transaction.executeWithoutResult(status -> peladaRepository.findById(pelada.getId())
                        .ifPresent(managed -> createNextDailyIfDue(managed, now)));
            } catch (RuntimeException e) {
                log.error("Auto-create daily failed for pelada {} (dayOfWeek={})", pelada.getId(), pelada.getDayOfWeek(), e);
            }
        });
    }

    private void createNextDailyIfDue(Pelada pelada, Instant instant) {
        LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneId.of(pelada.getTimeZone()));
        DayOfWeek target = DayOfWeek.valueOf(pelada.getDayOfWeek().toUpperCase(Locale.ROOT));
        LocalDate nextOccurrence = now.toLocalDate().with(TemporalAdjusters.nextOrSame(target));
        long hoursUntilNext = Duration.between(now, nextOccurrence.atStartOfDay()).toHours();

        if (hoursUntilNext < 0 || hoursUntilNext > 24) {
            return;
        }
        if (dailyRepository.existsByPeladaAndDailyDate(pelada, nextOccurrence)) {
            return;
        }
        Daily daily = dailyRepository.save(Daily.builder()
                .pelada(pelada)
                .dailyDate(nextOccurrence)
                .dailyTime(pelada.getTimeOfDay())
                .status(DailyStatus.SCHEDULED)
                .build());
        // No actor: every member hears about it
        eventPublisher.publishEvent(new DailyCreatedEvent(daily, null));
    }
}
