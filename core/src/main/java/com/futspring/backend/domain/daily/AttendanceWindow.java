package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.entity.Daily;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * When a session starts and from when diaristas may confirm themselves, as absolute instants computed from the
 * session's date and time in the pelada's time zone (never from the server's zone or anything the client sends).
 */
public final class AttendanceWindow {

    /** Diaristas confirm themselves from this long before the session starts; mensalistas any time. */
    public static final Duration DIARISTA_WINDOW = Duration.ofHours(24);

    private static final Map<DayOfWeek, String> WEEKDAYS = Map.of(
            DayOfWeek.MONDAY, "seg", DayOfWeek.TUESDAY, "ter", DayOfWeek.WEDNESDAY, "qua", DayOfWeek.THURSDAY, "qui",
            DayOfWeek.FRIDAY, "sex", DayOfWeek.SATURDAY, "sáb", DayOfWeek.SUNDAY, "dom");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");

    private AttendanceWindow() {
    }

    public static Instant startsAt(Daily daily) {
        return start(daily).toInstant();
    }

    public static Instant diaristaOpensAt(Daily daily) {
        return startsAt(daily).minus(DIARISTA_WINDOW);
    }

    /** "sáb, 31/10 às 20:00": when the diarista window opens, in the pelada's zone. */
    public static String formatOpening(Daily daily) {
        return format(start(daily).minus(DIARISTA_WINDOW));
    }

    /** "sex, 30/10 às 20:00": the session start, in the pelada's zone. */
    public static String formatStart(Daily daily) {
        return format(start(daily));
    }

    /** "sáb, 31/10": the session's day, as the session list shows it. */
    public static String formatDate(Daily daily) {
        return WEEKDAYS.get(daily.getDailyDate().getDayOfWeek()) + ", " + DATE.format(daily.getDailyDate());
    }

    private static ZonedDateTime start(Daily daily) {
        ZoneId zone = ZoneId.of(daily.getPelada().getTimeZone());
        return ZonedDateTime.of(daily.getDailyDate(), LocalTime.parse(daily.getDailyTime()), zone);
    }

    private static String format(ZonedDateTime at) {
        return WEEKDAYS.get(at.getDayOfWeek()) + ", " + DATE_TIME.format(at);
    }
}
