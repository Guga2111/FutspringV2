package com.futspring.backend.service;

import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.repository.DailyRepository;
import com.futspring.backend.repository.PeladaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DailySchedulerService {

    private final PeladaRepository peladaRepository;
    private final DailyRepository dailyRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void autoCreateDailies() {
        List<Pelada> peladas = peladaRepository.findByAutoCreateDailyEnabledTrue();
        LocalDateTime now = LocalDateTime.now();

        for (Pelada pelada : peladas) {
            LocalDate nextOccurrence = getNextOccurrence(pelada.getDayOfWeek(), now.toLocalDate());

            LocalDateTime nextOccurrenceDateTime = nextOccurrence.atStartOfDay();
            long hoursUntilNext = java.time.Duration.between(now, nextOccurrenceDateTime).toHours();

            if (hoursUntilNext >= 0 && hoursUntilNext <= 24) {
                boolean exists = dailyRepository.existsByPeladaAndDailyDate(pelada, nextOccurrence);
                if (!exists) {
                    Daily daily = Daily.builder()
                            .pelada(pelada)
                            .dailyDate(nextOccurrence)
                            .dailyTime(pelada.getTimeOfDay())
                            .status("SCHEDULED")
                            .build();
                    dailyRepository.save(daily);
                }
            }
        }
    }

    private LocalDate getNextOccurrence(String dayOfWeekName, LocalDate from) {
        DayOfWeek target = DayOfWeek.valueOf(dayOfWeekName.toUpperCase(Locale.ROOT));
        LocalDate date = from;
        for (int i = 0; i <= 7; i++) {
            if (date.getDayOfWeek() == target) {
                return date;
            }
            date = date.plusDays(1);
        }
        return from.plusDays(7);
    }
}
