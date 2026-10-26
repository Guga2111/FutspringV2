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
import com.futspring.backend.support.MembershipStubs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    PeladaRepository peladaRepository;
    @Mock
    UserAuthenticationHelper userAuthHelper;
    @Mock
    DailyRepository dailyRepository;
    @Mock
    UserDailyStatsRepository userDailyStatsRepository;

    AttendanceService attendanceService;

    User ana;
    User bruno;
    User carla;
    User outsider;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceService(peladaRepository, userAuthHelper,
                new PeladaAccessHelper(peladaRepository), dailyRepository, userDailyStatsRepository);

        ana = User.builder().id(1L).email("ana@example.com").username("ana").password("hash").build();
        bruno = User.builder().id(2L).email("bruno@example.com").username("Bruno").password("hash").build();
        carla = User.builder().id(3L).email("carla@example.com").username("carla").password("hash").build();
        outsider = User.builder().id(4L).email("out@example.com").username("out").password("hash").build();

        pelada = Pelada.builder()
                .id(10L)
                .name("Pelada")
                .dayOfWeek("FRIDAY")
                .timeOfDay("18:00")
                .duration(2f)
                .members(new HashSet<>(Set.of(ana, bruno, carla)))
                .admins(new HashSet<>(Set.of(ana)))
                .build();
        MembershipStubs.stubMembership(peladaRepository, pelada);
    }

    private void stubCaller(User caller) {
        when(userAuthHelper.getAuthenticatedUser(caller.getEmail())).thenReturn(caller);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
    }

    @Test
    void getAttendanceRanking_computesRatesOverTheLastSixMonthsAndSorts() {
        stubCaller(ana);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(6);
        when(dailyRepository.countByPeladaAndStatusInRange(10L, DailyStatus.FINISHED, from, to))
                .thenReturn(4L);
        when(userDailyStatsRepository.countAttendanceByUser(10L, DailyStatus.FINISHED, from, to))
                .thenReturn(List.of(new Object[]{1L, 2L}, new Object[]{2L, 4L}));

        PeladaAttendanceDTO result = attendanceService.getAttendanceRanking(10L, "ana@example.com");

        assertThat(result.getFrom()).isEqualTo(from);
        assertThat(result.getTo()).isEqualTo(to);
        assertThat(result.getTotalSessions()).isEqualTo(4);
        assertThat(result.getRows()).extracting(PeladaAttendanceDTO.Row::getUserId).containsExactly(2L, 1L, 3L);
        assertThat(result.getRows()).extracting(PeladaAttendanceDTO.Row::getSessionsAttended).containsExactly(4L, 2L, 0L);
        assertThat(result.getRows()).extracting(PeladaAttendanceDTO.Row::getRate).containsExactly(1.0, 0.5, 0.0);
    }

    @Test
    void getAttendanceRanking_tiesAreOrderedByUsername() {
        stubCaller(ana);
        when(dailyRepository.countByPeladaAndStatusInRange(eq(10L), eq(DailyStatus.FINISHED), any(), any()))
                .thenReturn(2L);
        when(userDailyStatsRepository.countAttendanceByUser(eq(10L), eq(DailyStatus.FINISHED), any(), any()))
                .thenReturn(List.of(new Object[]{1L, 1L}, new Object[]{2L, 1L}, new Object[]{3L, 1L}));

        PeladaAttendanceDTO result = attendanceService.getAttendanceRanking(10L, "ana@example.com");

        assertThat(result.getRows()).extracting(PeladaAttendanceDTO.Row::getUsername)
                .containsExactly("ana", "Bruno", "carla");
    }

    @Test
    void getAttendanceRanking_noFinishedSessions_everyRateIsZero() {
        stubCaller(bruno);
        when(dailyRepository.countByPeladaAndStatusInRange(eq(10L), eq(DailyStatus.FINISHED), any(), any()))
                .thenReturn(0L);
        when(userDailyStatsRepository.countAttendanceByUser(eq(10L), eq(DailyStatus.FINISHED), any(), any()))
                .thenReturn(List.of());

        PeladaAttendanceDTO result = attendanceService.getAttendanceRanking(10L, "bruno@example.com");

        assertThat(result.getTotalSessions()).isZero();
        assertThat(result.getRows()).hasSize(3).allSatisfy(row -> {
            assertThat(row.getRate()).isZero();
            assertThat(row.getSessionsAttended()).isZero();
        });
    }

    @Test
    void getAttendanceRanking_ignoresPlayersWhoAreNoLongerMembers() {
        stubCaller(ana);
        when(dailyRepository.countByPeladaAndStatusInRange(eq(10L), eq(DailyStatus.FINISHED), any(), any()))
                .thenReturn(3L);
        when(userDailyStatsRepository.countAttendanceByUser(eq(10L), eq(DailyStatus.FINISHED), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{4L, 3L}));

        PeladaAttendanceDTO result = attendanceService.getAttendanceRanking(10L, "ana@example.com");

        assertThat(result.getRows()).extracting(PeladaAttendanceDTO.Row::getUserId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void getAttendanceRanking_notMember_throwsForbidden() {
        stubCaller(outsider);

        assertThatThrownBy(() -> attendanceService.getAttendanceRanking(10L, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(dailyRepository, userDailyStatsRepository);
    }

    @Test
    void getAttendanceRanking_unknownPelada_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ana@example.com")).thenReturn(ana);
        when(peladaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.getAttendanceRanking(99L, "ana@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
