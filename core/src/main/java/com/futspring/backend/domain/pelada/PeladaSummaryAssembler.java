package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.daily.attendance.AttendanceWindow;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.dto.AttendanceBumpDTO;
import com.futspring.backend.domain.finance.FinancePreservation;
import com.futspring.backend.domain.pelada.dto.PeladaResponseDTO;
import com.futspring.backend.domain.pelada.member.PeladaMensalistaRepository;
import com.futspring.backend.domain.subscription.SubscriptionGuard;
import com.futspring.backend.domain.subscription.dto.SubscriptionSummaryDTO;
import com.futspring.backend.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The pelada cards of {@code /peladas/my} and {@code /users/{id}/peladas}: member count, the caller's admin and finance-manager flags,
 * next session with the caller's attendance, and subscription, in a fixed number of queries whatever the number of
 * peladas. Runs inside the caller's read-only transaction.
 */
@Component
@RequiredArgsConstructor
public class PeladaSummaryAssembler {

    private static final Set<DailyStatus> UPCOMING_STATUSES = Set.of(DailyStatus.SCHEDULED, DailyStatus.CONFIRMED);

    private final PeladaRepository peladaRepository;
    private final DailyRepository dailyRepository;
    private final DailyAttendanceRepository attendanceRepository;
    private final DailyAttendanceBumpRepository bumpRepository;
    private final PeladaMensalistaRepository mensalistaRepository;
    private final SubscriptionGuard subscriptionGuard;
    private final FinancePreservation financePreservation;
    private final Clock clock;

    // Member counts, admin and finance-manager flags, next sessions and subscriptions in grouped queries instead of one per pelada
    public List<PeladaResponseDTO> toSummaries(List<Pelada> peladas, User caller) {
        if (peladas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = peladas.stream().map(Pelada::getId).toList();
        Map<Long, Integer> memberCounts = peladaRepository.countMembersByIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
        Set<Long> administered = new HashSet<>(peladaRepository.findAdministeredIds(ids, caller.getId()));
        Set<Long> financeManaged = financePreservation.managedPeladaIds(caller.getId());

        // One instant for the entire response. Group by local day (at most three dates worldwide),
        // not by pelada, so zone correctness does not introduce an N+1 query.
        Instant now = clock.instant();
        Map<LocalDate, List<Pelada>> byToday = peladas.stream().collect(Collectors.groupingBy(
                p -> now.atZone(ZoneId.of(p.getTimeZone())).toLocalDate()));
        // Two sessions of a pelada on the same date: keep the oldest one.
        Map<Long, Daily> nextDailies = byToday.entrySet().stream()
                .flatMap(entry -> dailyRepository.findNextDailies(
                        entry.getValue().stream().map(Pelada::getId).toList(), UPCOMING_STATUSES, entry.getKey()).stream())
                .collect(Collectors.toMap(d -> d.getPelada().getId(), d -> d,
                        (a, b) -> a.getId() <= b.getId() ? a : b));
        List<Long> nextIds = nextDailies.values().stream().map(Daily::getId).toList();
        Map<Long, Integer> confirmedCounts = nextIds.isEmpty() ? Map.of()
                : attendanceRepository.countConfirmedByIds(nextIds).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
        Set<Long> confirmedByCaller = nextIds.isEmpty() ? Set.of()
                : new HashSet<>(attendanceRepository.findConfirmedDailyIds(nextIds, caller.getId()));
        Set<Long> mensalistaIn = new HashSet<>(mensalistaRepository.findPeladaIdsByUserAndPeladaIn(caller.getId(), ids));
        // Newest first, so the first bump kept per session is the caller's latest
        Map<Long, AttendanceBumpDTO> unseenBumps = nextIds.isEmpty() ? Map.of()
                : bumpRepository.findUnseenByUserAndDailyIn(caller.getId(), nextIds).stream()
                        .collect(Collectors.toMap(b -> b.getDaily().getId(), AttendanceBumpDTO::from, (newest, older) -> newest));
        Map<Long, SubscriptionSummaryDTO> subscriptions = subscriptionGuard.summaries(ids);

        return peladas.stream()
                .map(p -> {
                    Daily next = nextDailies.get(p.getId());
                    PeladaResponseDTO.NextDailyDTO nextDaily = next == null ? null : PeladaResponseDTO.NextDailyDTO.builder()
                            .id(next.getId())
                            .date(next.getDailyDate())
                            .time(next.getDailyTime())
                            .status(next.getStatus())
                            .confirmedCount(confirmedCounts.getOrDefault(next.getId(), 0))
                            .capacity(p.capacity())
                            .isConfirmed(confirmedByCaller.contains(next.getId()))
                            .startsAt(AttendanceWindow.startsAt(next))
                            .confirmOpensAt(mensalistaIn.contains(p.getId()) ? null : AttendanceWindow.diaristaOpensAt(next))
                            .bump(unseenBumps.get(next.getId()))
                            .build();
                    return PeladaResponseDTO.from(p, memberCounts.getOrDefault(p.getId(), 0),
                            administered.contains(p.getId()), financeManaged.contains(p.getId()), nextDaily,
                            subscriptions.get(p.getId()));
                })
                .toList();
    }
}
