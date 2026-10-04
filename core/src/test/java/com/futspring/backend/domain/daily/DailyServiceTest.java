package com.futspring.backend.domain.daily;

import com.futspring.backend.domain.daily.dto.CreateDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO;
import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.daily.repository.LeagueTableEntryRepository;
import com.futspring.backend.domain.daily.repository.MatchRepository;
import com.futspring.backend.domain.daily.repository.PlayerMatchStatRepository;
import com.futspring.backend.domain.daily.repository.TeamRepository;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.stats.AggregateRebuildService;
import com.futspring.backend.domain.stats.repository.DailyAwardRepository;
import com.futspring.backend.domain.stats.repository.UserDailyStatsRepository;
import com.futspring.backend.support.MembershipStubs;
import com.futspring.backend.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyServiceTest {

    @Mock AggregateRebuildService aggregateRebuildService;
    @Mock FileUploadService fileUploadService;
    @Mock UserAuthenticationHelper userAuthHelper;
    @Mock DailyAttendanceService dailyAttendanceService;
    @Mock DailyTeamManagementService dailyTeamManagementService;
    @Mock DailyResultsService dailyResultsService;
    @Mock DailyDTOMapper dailyDTOMapper;
    @Mock DailyRepository dailyRepository;
    @Mock PeladaRepository peladaRepository;
    @Mock TeamRepository teamRepository;
    @Mock MatchRepository matchRepository;
    @Mock PlayerMatchStatRepository playerMatchStatRepository;
    @Mock UserDailyStatsRepository userDailyStatsRepository;
    @Mock LeagueTableEntryRepository leagueTableEntryRepository;
    @Mock DailyAwardRepository dailyAwardRepository;

    DailyService dailyService;

    User admin;
    User member;
    User outsider;
    Pelada pelada;
    Daily scheduledDaily;

    @BeforeEach
    void setUp() {
        dailyService = new DailyService(
                userAuthHelper, new PeladaAccessHelper(peladaRepository), dailyAttendanceService, dailyTeamManagementService,
                dailyResultsService, aggregateRebuildService, fileUploadService, dailyDTOMapper,
                dailyRepository, peladaRepository, teamRepository,
                matchRepository, playerMatchStatRepository, userDailyStatsRepository,
                leagueTableEntryRepository, dailyAwardRepository,
                new DailyListItemAssembler(dailyRepository, teamRepository, matchRepository));

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").stars(4).build();
        member = User.builder().id(2L).email("member@example.com").username("member").password("hash").stars(3).build();
        outsider = User.builder().id(3L).email("out@example.com").username("out").password("hash").stars(3).build();

        pelada = Pelada.builder()
                .id(10L)
                .name("Pelada")
                .dayOfWeek("FRIDAY")
                .timeOfDay("18:00")
                .duration(2f)
                .numberOfTeams(2)
                .playersPerTeam(1)
                .members(new HashSet<>(Set.of(admin, member)))
                .admins(new HashSet<>(Set.of(admin)))
                .creator(admin)
                .build();

        scheduledDaily = Daily.builder()
                .id(100L)
                .pelada(pelada)
                .dailyDate(LocalDate.now().plusDays(3))
                .dailyTime("18:00")
                .status(DailyStatus.SCHEDULED)
                .confirmedPlayers(new HashSet<>())
                .build();
        MembershipStubs.stubMembership(peladaRepository, pelada);
    }

    // --- createDaily ---

    @Test
    void createDaily_success_returnsDTO() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyRepository.save(any(Daily.class))).thenAnswer(inv -> {
            Daily d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "id", 100L);
            return d;
        });

        CreateDailyRequestDTO req = new CreateDailyRequestDTO();
        req.setDailyDate(LocalDate.now().plusDays(7));
        req.setDailyTime("18:00");

        DailyListItemDTO result = dailyService.createDaily(10L, req, "admin@example.com");

        assertThat(result).isNotNull();
    }

    @Test
    void createDaily_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        CreateDailyRequestDTO req = new CreateDailyRequestDTO();
        req.setDailyDate(LocalDate.now().plusDays(7));
        req.setDailyTime("18:00");

        assertThatThrownBy(() -> dailyService.createDaily(10L, req, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createDaily_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        CreateDailyRequestDTO req = new CreateDailyRequestDTO();
        req.setDailyDate(LocalDate.now().plusDays(7));
        req.setDailyTime("18:00");

        assertThatThrownBy(() -> dailyService.createDaily(999L, req, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createDaily_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        CreateDailyRequestDTO req = new CreateDailyRequestDTO();
        req.setDailyDate(LocalDate.now().plusDays(7));
        req.setDailyTime("18:00");

        assertThatThrownBy(() -> dailyService.createDaily(10L, req, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- getDailiesForPelada ---

    @Test
    void getDailiesForPelada_success_returnsList() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyRepository.findByPeladaOrderByDailyDateDesc(pelada)).thenReturn(List.of(scheduledDaily));

        List<DailyListItemDTO> result = dailyService.getDailiesForPelada(10L, "member@example.com");

        assertThat(result).hasSize(1);
    }

    @Test
    void getDailiesForPelada_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> dailyService.getDailiesForPelada(10L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDailiesForPelada_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyService.getDailiesForPelada(999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDailiesForPelada_callerNotMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> dailyService.getDailiesForPelada(10L, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getDailiesForPelada_returnsDescOrder() {
        Daily older = Daily.builder().id(1L).pelada(pelada).dailyDate(LocalDate.of(2024, 1, 1))
                .dailyTime("18:00").status(DailyStatus.FINISHED).confirmedPlayers(new HashSet<>()).build();
        Daily newer = Daily.builder().id(2L).pelada(pelada).dailyDate(LocalDate.of(2024, 3, 1))
                .dailyTime("18:00").status(DailyStatus.SCHEDULED).confirmedPlayers(new HashSet<>()).build();

        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyRepository.findByPeladaOrderByDailyDateDesc(pelada)).thenReturn(List.of(newer, older));

        List<DailyListItemDTO> result = dailyService.getDailiesForPelada(10L, "member@example.com");

        assertThat(result.get(0).getDailyDate()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(result.get(1).getDailyDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    // --- getDailyDetail ---

    @Test
    void getDailyDetail_success_returnsDTO() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDailyWithPlayers(scheduledDaily)).thenReturn(Collections.emptyList());
        when(matchRepository.findByDailyWithTeams(scheduledDaily)).thenReturn(Collections.emptyList());
        when(userDailyStatsRepository.findByDailyWithUser(scheduledDaily)).thenReturn(Collections.emptyList());
        when(leagueTableEntryRepository.findByDailyWithTeam(scheduledDaily)).thenReturn(Collections.emptyList());
        when(dailyAwardRepository.findByDaily(scheduledDaily)).thenReturn(Optional.empty());

        DailyDetailDTO result = dailyService.getDailyDetail(100L, "member@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DailyStatus.SCHEDULED);
    }

    @Test
    void getDailyDetail_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> dailyService.getDailyDetail(100L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDailyDetail_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyService.getDailyDetail(999L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDailyDetail_callerNotMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.getDailyDetail(100L, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- updateStatus ---

    @Test
    void updateStatus_scheduledToConfirmed_succeeds() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dailyService.updateStatus(100L, DailyStatus.CONFIRMED, "admin@example.com");

        assertThat(scheduledDaily.getStatus()).isEqualTo(DailyStatus.CONFIRMED);
    }

    @Test
    void updateStatus_scheduledToCanceled_succeeds() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dailyService.updateStatus(100L, DailyStatus.CANCELED, "admin@example.com");

        assertThat(scheduledDaily.getStatus()).isEqualTo(DailyStatus.CANCELED);
    }

    @Test
    void updateStatus_confirmedToInCourse_succeeds() {
        scheduledDaily.setStatus(DailyStatus.CONFIRMED);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dailyService.updateStatus(100L, DailyStatus.IN_COURSE, "admin@example.com");

        assertThat(scheduledDaily.getStatus()).isEqualTo(DailyStatus.IN_COURSE);
    }

    @Test
    void updateStatus_invalidTransition_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.updateStatus(100L, DailyStatus.IN_COURSE, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateStatus_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.updateStatus(100L, DailyStatus.CONFIRMED, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateStatus_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> dailyService.updateStatus(100L, DailyStatus.CONFIRMED, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateStatus_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyService.updateStatus(999L, DailyStatus.CONFIRMED, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateStatus_finishedToAnyStatus_throwsBadRequest() {
        scheduledDaily.setStatus(DailyStatus.FINISHED);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.updateStatus(100L, DailyStatus.SCHEDULED, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- deleteDaily ---

    @Test
    void deleteDaily_success_orchestratesSubServicesAndDeletes() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        when(dailyResultsService.clearResults(scheduledDaily)).thenReturn(Set.of(member));

        dailyService.deleteDaily(100L, "admin@example.com");

        verify(dailyResultsService).clearResults(scheduledDaily);
        verify(aggregateRebuildService).rebuild(pelada, Set.of(member));
        verify(dailyTeamManagementService).clearTeams(scheduledDaily);
        verify(dailyAttendanceService).clearAttendees(scheduledDaily);
        verify(dailyRepository).delete(scheduledDaily);
    }

    @Test
    void deleteDaily_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.deleteDaily(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(dailyResultsService, never()).clearResults(any());
        verify(dailyRepository, never()).delete(any(Daily.class));
    }

    @Test
    void deleteDaily_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyService.deleteDaily(999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteDaily_callerNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> dailyService.deleteDaily(100L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
