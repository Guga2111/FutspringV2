package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailySchedulerServiceTest {

    @Mock
    PeladaRepository peladaRepository;
    @Mock
    DailyRepository dailyRepository;

    DailySchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        schedulerService = new DailySchedulerService(peladaRepository, dailyRepository);
    }

    // The scheduler creates a daily the day before (next occurrence's midnight 0-24 h ahead), so tests
    // use tomorrow's pelada; today's only matches between 00:00 and 00:59 and made the suite time-dependent
    private static LocalDate tomorrow() {
        return LocalDate.now().plusDays(1);
    }

    private Pelada peladaForDay(DayOfWeek day) {
        return Pelada.builder()
                .id(1L)
                .name("Pelada")
                .dayOfWeek(day.name())
                .timeOfDay("18:00")
                .duration(2f)
                .autoCreateDailyEnabled(true)
                .build();
    }

    @Test
    void autoCreateDailies_nopeladasEnabled_doesNothing() {
        when(peladaRepository.findByAutoCreateDailyEnabledTrue()).thenReturn(Collections.emptyList());

        schedulerService.autoCreateDailies();

        verify(dailyRepository, never()).save(any());
    }

    @Test
    void autoCreateDailies_createsDailyWithCorrectStatus() {
        DayOfWeek tomorrowDow = tomorrow().getDayOfWeek();
        Pelada pelada = peladaForDay(tomorrowDow);

        when(peladaRepository.findByAutoCreateDailyEnabledTrue()).thenReturn(List.of(pelada));
        when(dailyRepository.existsByPeladaAndDailyDate(eq(pelada), any())).thenReturn(false);
        when(dailyRepository.save(any(Daily.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulerService.autoCreateDailies();

        ArgumentCaptor<Daily> captor = ArgumentCaptor.forClass(Daily.class);
        verify(dailyRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DailyStatus.SCHEDULED);
    }

    @Test
    void autoCreateDailies_createsDailyWithCorrectTimeOfDay() {
        DayOfWeek tomorrowDow = tomorrow().getDayOfWeek();
        Pelada pelada = peladaForDay(tomorrowDow);

        when(peladaRepository.findByAutoCreateDailyEnabledTrue()).thenReturn(List.of(pelada));
        when(dailyRepository.existsByPeladaAndDailyDate(eq(pelada), any())).thenReturn(false);
        when(dailyRepository.save(any(Daily.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulerService.autoCreateDailies();

        ArgumentCaptor<Daily> captor = ArgumentCaptor.forClass(Daily.class);
        verify(dailyRepository).save(captor.capture());
        assertThat(captor.getValue().getDailyTime()).isEqualTo("18:00");
    }

    @Test
    void autoCreateDailies_dailyAlreadyExists_doesNotCreate() {
        DayOfWeek tomorrowDow = tomorrow().getDayOfWeek();
        Pelada pelada = peladaForDay(tomorrowDow);

        when(peladaRepository.findByAutoCreateDailyEnabledTrue()).thenReturn(List.of(pelada));
        when(dailyRepository.existsByPeladaAndDailyDate(eq(pelada), any())).thenReturn(true);

        schedulerService.autoCreateDailies();

        verify(dailyRepository, never()).save(any());
    }

    @Test
    void autoCreateDailies_multiplePeladas_processedIndependently() {
        DayOfWeek tomorrowDow = tomorrow().getDayOfWeek();

        Pelada pelada1 = Pelada.builder().id(1L).name("P1").dayOfWeek(tomorrowDow.name())
                .timeOfDay("18:00").duration(2f).autoCreateDailyEnabled(true).build();
        Pelada pelada2 = Pelada.builder().id(2L).name("P2").dayOfWeek(tomorrowDow.name())
                .timeOfDay("20:00").duration(1.5f).autoCreateDailyEnabled(true).build();

        when(peladaRepository.findByAutoCreateDailyEnabledTrue()).thenReturn(List.of(pelada1, pelada2));
        when(dailyRepository.existsByPeladaAndDailyDate(eq(pelada1), any())).thenReturn(false);
        when(dailyRepository.existsByPeladaAndDailyDate(eq(pelada2), any())).thenReturn(false);
        when(dailyRepository.save(any(Daily.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulerService.autoCreateDailies();

        verify(dailyRepository, times(2)).save(any(Daily.class));
    }

    @Test
    void autoCreateDailies_onePeladaExists_onlyCreatesForOther() {
        DayOfWeek tomorrowDow = tomorrow().getDayOfWeek();

        Pelada pelada1 = Pelada.builder().id(1L).name("P1").dayOfWeek(tomorrowDow.name())
                .timeOfDay("18:00").duration(2f).autoCreateDailyEnabled(true).build();
        Pelada pelada2 = Pelada.builder().id(2L).name("P2").dayOfWeek(tomorrowDow.name())
                .timeOfDay("20:00").duration(1.5f).autoCreateDailyEnabled(true).build();

        when(peladaRepository.findByAutoCreateDailyEnabledTrue()).thenReturn(List.of(pelada1, pelada2));
        when(dailyRepository.existsByPeladaAndDailyDate(eq(pelada1), any())).thenReturn(true);
        when(dailyRepository.existsByPeladaAndDailyDate(eq(pelada2), any())).thenReturn(false);
        when(dailyRepository.save(any(Daily.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulerService.autoCreateDailies();

        verify(dailyRepository, times(1)).save(any(Daily.class));
    }

    @Test
    void autoCreateDailies_createdDailyBelongsToPelada() {
        DayOfWeek tomorrowDow = tomorrow().getDayOfWeek();
        Pelada pelada = peladaForDay(tomorrowDow);

        when(peladaRepository.findByAutoCreateDailyEnabledTrue()).thenReturn(List.of(pelada));
        when(dailyRepository.existsByPeladaAndDailyDate(eq(pelada), any())).thenReturn(false);
        when(dailyRepository.save(any(Daily.class))).thenAnswer(inv -> inv.getArgument(0));

        schedulerService.autoCreateDailies();

        ArgumentCaptor<Daily> captor = ArgumentCaptor.forClass(Daily.class);
        verify(dailyRepository).save(captor.capture());
        assertThat(captor.getValue().getPelada()).isEqualTo(pelada);
        assertThat(captor.getValue().getDailyDate()).isEqualTo(tomorrow());
    }
}
