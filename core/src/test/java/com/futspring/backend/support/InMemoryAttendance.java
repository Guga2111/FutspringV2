package com.futspring.backend.support;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyAttendance;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.user.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests mock DailyAttendanceRepository. These lenient stubs back it with an in-memory list of
 * attendance rows, so services that confirm, count and remove players behave as with a real database,
 * and tests can seed and assert the session's attendance.
 */
public final class InMemoryAttendance {

    private static final Comparator<DailyAttendance> CONFIRMATION_ORDER =
            Comparator.comparing(DailyAttendance::getConfirmedAt).thenComparing(a -> a.getUser().getId());

    private final List<DailyAttendance> rows = new ArrayList<>();

    private InMemoryAttendance() {
    }

    public static InMemoryAttendance stub(DailyAttendanceRepository repository) {
        InMemoryAttendance attendance = new InMemoryAttendance();
        List<DailyAttendance> rows = attendance.rows;
        lenient().when(repository.save(any(DailyAttendance.class))).thenAnswer(inv -> {
            DailyAttendance row = inv.getArgument(0);
            rows.add(row);
            return row;
        });
        lenient().when(repository.saveAll(anyIterable())).thenAnswer(inv -> {
            List<DailyAttendance> saved = new ArrayList<>();
            inv.<Iterable<DailyAttendance>>getArgument(0).forEach(saved::add);
            rows.addAll(saved);
            return saved;
        });
        lenient().when(repository.existsByDailyAndUser(any(), any())).thenAnswer(inv ->
                rows.stream().anyMatch(a -> a.getDaily().equals(inv.getArgument(0)) && a.getUser().equals(inv.getArgument(1))));
        lenient().when(repository.countByDaily(any())).thenAnswer(inv ->
                rows.stream().filter(a -> a.getDaily().equals(inv.getArgument(0))).count());
        lenient().when(repository.findByDailyWithUser(any())).thenAnswer(inv -> attendance.rows(inv.getArgument(0)));
        lenient().when(repository.findUsersByDaily(any())).thenCallRealMethod();
        lenient().when(repository.deleteByDailyAndUser(any(), any())).thenAnswer(inv -> {
            int before = rows.size();
            rows.removeIf(a -> a.getDaily().equals(inv.getArgument(0)) && a.getUser().equals(inv.getArgument(1)));
            return before - rows.size();
        });
        lenient().doAnswer(inv -> rows.removeIf(a -> a.getDaily().equals(inv.getArgument(0))))
                .when(repository).deleteAllOfDaily(any());
        lenient().doAnswer(inv -> rows.remove(inv.<DailyAttendance>getArgument(0)))
                .when(repository).delete(any(DailyAttendance.class));
        return attendance;
    }

    /** Confirms the players after every row already there, one second apart and in the given order. */
    public void confirm(Daily daily, User... players) {
        for (User player : players) {
            confirm(daily, player, Instant.parse("2026-01-01T12:00:00Z").plusSeconds(rows.size()));
        }
    }

    public void confirm(Daily daily, User player, Instant confirmedAt) {
        rows.add(new DailyAttendance(daily, player, confirmedAt));
    }

    /** The session's attendance rows, in confirmation order. */
    public List<DailyAttendance> rows(Daily daily) {
        return rows.stream().filter(a -> a.getDaily().equals(daily)).sorted(CONFIRMATION_ORDER).toList();
    }

    /** The session's confirmed players, in confirmation order. */
    public List<User> users(Daily daily) {
        return rows(daily).stream().map(DailyAttendance::getUser).toList();
    }
}
