package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailySchedulerService {

    private final PeladaRepository peladaRepository;
    private final DailyRepository dailyRepository;

    // Hourly, server-local time: creates the next daily of each pelada once it is at most 24 h away
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void autoCreateDailies() {
        List<Pelada> peladas = peladaRepository.findByAutoCreateDailyEnabledTrue();
        LocalDateTime now = LocalDateTime.now();

        for (Pelada pelada : peladas) {
            // One bad pelada must not abort the run for the others
            try {
                createNextDailyIfDue(pelada, now);
            } catch (RuntimeException e) {
                log.error("Auto-create daily failed for pelada {} (dayOfWeek={})", pelada.getId(), pelada.getDayOfWeek(), e);
            }
        }
    }

    private void createNextDailyIfDue(Pelada pelada, LocalDateTime now) {
        DayOfWeek target = DayOfWeek.valueOf(pelada.getDayOfWeek().toUpperCase(Locale.ROOT));
        LocalDate nextOccurrence = now.toLocalDate().with(TemporalAdjusters.nextOrSame(target));
        long hoursUntilNext = Duration.between(now, nextOccurrence.atStartOfDay()).toHours();

        if (hoursUntilNext < 0 || hoursUntilNext > 24) {
            return;
        }
        if (dailyRepository.existsByPeladaAndDailyDate(pelada, nextOccurrence)) {
            return;
        }
        dailyRepository.save(Daily.builder()
                .pelada(pelada)
                .dailyDate(nextOccurrence)
                .dailyTime(pelada.getTimeOfDay())
                .status(DailyStatus.SCHEDULED)
                .build());
    }
}
