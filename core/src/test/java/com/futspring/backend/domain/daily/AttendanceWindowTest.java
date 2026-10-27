package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.pelada.Pelada;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceWindowTest {

    @Test
    void startAndOpening_areComputedInThePeladaZone() {
        Daily saoPaulo = daily("America/Sao_Paulo", LocalDate.of(2026, 10, 30), "20:00");
        Daily toronto = daily("America/Toronto", LocalDate.of(2026, 10, 30), "20:00");

        assertThat(AttendanceWindow.startsAt(saoPaulo)).isEqualTo(Instant.parse("2026-10-30T23:00:00Z"));
        assertThat(AttendanceWindow.diaristaOpensAt(saoPaulo)).isEqualTo(Instant.parse("2026-10-29T23:00:00Z"));
        // Toronto is still on daylight time (UTC-4) on Oct 30
        assertThat(AttendanceWindow.startsAt(toronto)).isEqualTo(Instant.parse("2026-10-31T00:00:00Z"));
        assertThat(AttendanceWindow.diaristaOpensAt(toronto)).isEqualTo(Instant.parse("2026-10-30T00:00:00Z"));
    }

    @Test
    void opening_isTwentyFourRealHoursBeforeTheStart_acrossTheCanadianDstChange() {
        // Daylight time ends in Toronto on 2026-11-01 at 02:00; the session that evening is on standard time (UTC-5)
        Daily afterTheChange = daily("America/Toronto", LocalDate.of(2026, 11, 1), "20:00");

        assertThat(AttendanceWindow.startsAt(afterTheChange)).isEqualTo(Instant.parse("2026-11-02T01:00:00Z"));
        assertThat(AttendanceWindow.diaristaOpensAt(afterTheChange)).isEqualTo(Instant.parse("2026-11-01T01:00:00Z"));
        // 24 h earlier the clocks were still on daylight time, so the window opens at 21:00 local
        assertThat(AttendanceWindow.formatOpening(afterTheChange)).isEqualTo("sáb, 31/10 às 21:00");
    }

    @Test
    void format_usesShortPortugueseWeekdays() {
        Daily daily = daily("America/Sao_Paulo", LocalDate.of(2026, 10, 31), "08:30");

        assertThat(AttendanceWindow.formatStart(daily)).isEqualTo("sáb, 31/10 às 08:30");
        assertThat(AttendanceWindow.formatOpening(daily)).isEqualTo("sex, 30/10 às 08:30");
    }

    private static Daily daily(String zone, LocalDate date, String time) {
        Pelada pelada = Pelada.builder().id(1L).timeZone(zone).build();
        return Daily.builder().id(1L).pelada(pelada).dailyDate(date).dailyTime(time).build();
    }
}
