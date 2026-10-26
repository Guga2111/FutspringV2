package com.futspring.backend.domain.stats;

import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.stats.dto.PeladaAttendanceDTO;
import com.futspring.backend.domain.stats.repository.UserDailyStatsRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    public static final int PERIOD_MONTHS = 6;

    private final PeladaRepository peladaRepository;
    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyRepository dailyRepository;
    private final UserDailyStatsRepository userDailyStatsRepository;

    // Presence = being on a team of a finalized session (a UserDailyStats row); the denominator is every
    // finalized session of the pelada in the last PERIOD_MONTHS months. Constant number of queries.
    @Transactional(readOnly = true)
    public PeladaAttendanceDTO getAttendanceRanking(Long peladaId, String callerEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        accessHelper.requireMember(pelada, caller);

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(PERIOD_MONTHS);

        long totalSessions = dailyRepository.countByPeladaAndStatusInRange(
                peladaId, DailyStatus.FINISHED, from, to);
        Map<Long, Long> attendedByUserId = userDailyStatsRepository
                .countAttendanceByUser(peladaId, DailyStatus.FINISHED, from, to).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));

        // Former members who played in the period are left out
        List<PeladaAttendanceDTO.Row> rows = pelada.getMembers().stream()
                .map(member -> {
                    long attended = attendedByUserId.getOrDefault(member.getId(), 0L);
                    return PeladaAttendanceDTO.Row.builder()
                            .userId(member.getId())
                            .username(member.getUsername())
                            .userImage(member.getImage())
                            .sessionsAttended(attended)
                            .rate(totalSessions == 0 ? 0 : attended / (double) totalSessions)
                            .build();
                })
                .sorted(Comparator.comparingDouble(PeladaAttendanceDTO.Row::getRate).reversed()
                        .thenComparing(Comparator.comparingLong(PeladaAttendanceDTO.Row::getSessionsAttended).reversed())
                        .thenComparing(PeladaAttendanceDTO.Row::getUsername, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        return PeladaAttendanceDTO.builder()
                .from(from)
                .to(to)
                .totalSessions(totalSessions)
                .rows(rows)
                .build();
    }
}
