package com.futspring.backend.service;

import com.futspring.backend.dto.CreateDailyRequestDTO;
import com.futspring.backend.dto.DailyDetailDTO;
import com.futspring.backend.dto.DailyListItemDTO;
import com.futspring.backend.entity.*;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.*;
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

    @Mock FileUploadService fileUploadService;
    @Mock UserAuthenticationHelper userAuthHelper;
    @Mock DailyAttendanceService dailyAttendanceService;
    @Mock DailyTeamManagementService dailyTeamManagementService;
    @Mock DailyResultsService dailyResultsService;
    @Mock DailyDTOMapper dailyDTOMapper;
    @Mock DailyRepository dailyRepository;
    @Mock PeladaRepository peladaRepository;
    @Mock UserRepository userRepository;
    @Mock TeamRepository teamRepository;
    @Mock MatchRepository matchRepository;
    @Mock PlayerMatchStatRepository playerMatchStatRepository;
    @Mock UserDailyStatsRepository userDailyStatsRepository;
    @Mock LeagueTableEntryRepository leagueTableEntryRepository;
    @Mock DailyAwardRepository dailyAwardRepository;
    @Mock StatsRepository statsRepository;
    @Mock RankingRepository rankingRepository;

    DailyService dailyService;

    User admin;
    User member;
    User outsider;
    Pelada pelada;
    Daily scheduledDaily;

    @BeforeEach
    void setUp() {
        dailyService = new DailyService(
                fileUploadService, userAuthHelper, dailyAttendanceService,
                dailyTeamManagementService, dailyResultsService, dailyDTOMapper,
                dailyRepository, peladaRepository, userRepository, teamRepository,
                matchRepository, playerMatchStatRepository, userDailyStatsRepository,
                leagueTableEntryRepository, dailyAwardRepository, statsRepository, rankingRepository);

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
                .status("SCHEDULED")
                .confirmedPlayers(new HashSet<>())
                .build();
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
    void createDaily_callerNotFound_throwsUnauthorized() {
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
    void getDailiesForPelada_callerNotFound_throwsUnauthorized() {
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
                .dailyTime("18:00").status("FINISHED").confirmedPlayers(new HashSet<>()).build();
        Daily newer = Daily.builder().id(2L).pelada(pelada).dailyDate(LocalDate.of(2024, 3, 1))
                .dailyTime("18:00").status("SCHEDULED").confirmedPlayers(new HashSet<>()).build();

        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        // Repository already returns in desc order
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
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());
        when(matchRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());
        when(userDailyStatsRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());
        when(leagueTableEntryRepository.findByDailyOrderByPositionAsc(scheduledDaily)).thenReturn(Collections.emptyList());
        when(dailyAwardRepository.findByDaily(scheduledDaily)).thenReturn(Optional.empty());

        DailyDetailDTO result = dailyService.getDailyDetail(100L, "member@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    void getDailyDetail_callerNotFound_throwsUnauthorized() {
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

    // --- confirmAttendance (delegation) ---

    @Test
    void confirmAttendance_delegatesToAttendanceService() {
        DailyListItemDTO expected = DailyListItemDTO.from(scheduledDaily);
        when(dailyAttendanceService.confirmAttendance(100L, "member@example.com")).thenReturn(expected);

        DailyListItemDTO result = dailyService.confirmAttendance(100L, "member@example.com");

        verify(dailyAttendanceService).confirmAttendance(100L, "member@example.com");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void confirmAttendance_propagatesException() {
        when(dailyAttendanceService.confirmAttendance(100L, "member@example.com"))
                .thenThrow(new AppException(HttpStatus.CONFLICT, "Already confirmed"));

        assertThatThrownBy(() -> dailyService.confirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // --- disconfirmAttendance (delegation) ---

    @Test
    void disconfirmAttendance_delegatesToAttendanceService() {
        DailyListItemDTO expected = DailyListItemDTO.from(scheduledDaily);
        when(dailyAttendanceService.disconfirmAttendance(100L, "member@example.com")).thenReturn(expected);

        DailyListItemDTO result = dailyService.disconfirmAttendance(100L, "member@example.com");

        verify(dailyAttendanceService).disconfirmAttendance(100L, "member@example.com");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void disconfirmAttendance_propagatesException() {
        when(dailyAttendanceService.disconfirmAttendance(100L, "member@example.com"))
                .thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Not confirmed"));

        assertThatThrownBy(() -> dailyService.disconfirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- updateStatus ---

    @Test
    void updateStatus_scheduledToConfirmed_succeeds() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DailyListItemDTO result = dailyService.updateStatus(100L, "CONFIRMED", "admin@example.com");

        assertThat(scheduledDaily.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void updateStatus_scheduledToCanceled_succeeds() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dailyService.updateStatus(100L, "CANCELED", "admin@example.com");

        assertThat(scheduledDaily.getStatus()).isEqualTo("CANCELED");
    }

    @Test
    void updateStatus_confirmedToInCourse_succeeds() {
        scheduledDaily.setStatus("CONFIRMED");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dailyService.updateStatus(100L, "IN_COURSE", "admin@example.com");

        assertThat(scheduledDaily.getStatus()).isEqualTo("IN_COURSE");
    }

    @Test
    void updateStatus_invalidTransition_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        // SCHEDULED -> IN_COURSE is invalid (must go through CONFIRMED first)
        assertThatThrownBy(() -> dailyService.updateStatus(100L, "IN_COURSE", "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateStatus_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.updateStatus(100L, "CONFIRMED", "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateStatus_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> dailyService.updateStatus(100L, "CONFIRMED", "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateStatus_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyService.updateStatus(999L, "CONFIRMED", "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateStatus_finishedToAnyStatus_throwsBadRequest() {
        scheduledDaily.setStatus("FINISHED");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        // FINISHED has no valid transitions
        assertThatThrownBy(() -> dailyService.updateStatus(100L, "SCHEDULED", "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- sortTeams (delegation) ---

    @Test
    void sortTeams_delegatesToTeamManagementService() {
        List<DailyDetailDTO.TeamDTO> expected = List.of();
        when(dailyTeamManagementService.sortTeams(100L, "admin@example.com")).thenReturn(expected);

        List<DailyDetailDTO.TeamDTO> result = dailyService.sortTeams(100L, "admin@example.com");

        verify(dailyTeamManagementService).sortTeams(100L, "admin@example.com");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void sortTeams_propagatesException() {
        when(dailyTeamManagementService.sortTeams(100L, "admin@example.com"))
                .thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Cannot sort teams"));

        assertThatThrownBy(() -> dailyService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- swapPlayers (delegation) ---

    @Test
    void swapPlayers_delegatesToTeamManagementService() {
        List<DailyDetailDTO.TeamDTO> expected = List.of();
        when(dailyTeamManagementService.swapPlayers(100L, 1L, 2L, "admin@example.com")).thenReturn(expected);

        List<DailyDetailDTO.TeamDTO> result = dailyService.swapPlayers(100L, 1L, 2L, "admin@example.com");

        verify(dailyTeamManagementService).swapPlayers(100L, 1L, 2L, "admin@example.com");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void swapPlayers_propagatesException() {
        when(dailyTeamManagementService.swapPlayers(100L, 1L, 2L, "admin@example.com"))
                .thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Player not on any team"));

        assertThatThrownBy(() -> dailyService.swapPlayers(100L, 1L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- submitResults (delegation) ---

    @Test
    void submitResults_delegatesToResultsService() {
        List<DailyDetailDTO.MatchDTO> expected = List.of();
        when(dailyResultsService.submitResults(100L, List.of(), "admin@example.com")).thenReturn(expected);

        List<DailyDetailDTO.MatchDTO> result = dailyService.submitResults(100L, List.of(), "admin@example.com");

        verify(dailyResultsService).submitResults(100L, List.of(), "admin@example.com");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void submitResults_propagatesException() {
        when(dailyResultsService.submitResults(100L, List.of(), "admin@example.com"))
                .thenThrow(new AppException(HttpStatus.FORBIDDEN, "Only admins can submit results"));

        assertThatThrownBy(() -> dailyService.submitResults(100L, List.of(), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- finalizeDaily (delegation) ---

    @Test
    void finalizeDaily_delegatesToResultsServiceThenCallsGetDetail() {
        doNothing().when(dailyResultsService).finalizeDaily(100L, List.of(), List.of(), "admin@example.com");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());
        when(matchRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());
        when(userDailyStatsRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());
        when(leagueTableEntryRepository.findByDailyOrderByPositionAsc(scheduledDaily)).thenReturn(Collections.emptyList());
        when(dailyAwardRepository.findByDaily(scheduledDaily)).thenReturn(Optional.empty());

        DailyDetailDTO result = dailyService.finalizeDaily(100L, List.of(), List.of(), "admin@example.com");

        verify(dailyResultsService).finalizeDaily(100L, List.of(), List.of(), "admin@example.com");
        assertThat(result).isNotNull();
    }

    @Test
    void finalizeDaily_propagatesException() {
        doThrow(new AppException(HttpStatus.BAD_REQUEST, "No matches to finalize"))
                .when(dailyResultsService).finalizeDaily(100L, List.of(), List.of(), "admin@example.com");

        assertThatThrownBy(() -> dailyService.finalizeDaily(100L, List.of(), List.of(), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- populateFromMessage (delegation) ---

    @Test
    void populateFromMessage_delegatesToResultsServiceThenCallsGetDetail() {
        com.futspring.backend.dto.PopulateDailyRequestDTO request = new com.futspring.backend.dto.PopulateDailyRequestDTO();
        doNothing().when(dailyResultsService).populateFromMessage(100L, request, "admin@example.com");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());
        when(matchRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());
        when(userDailyStatsRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());
        when(leagueTableEntryRepository.findByDailyOrderByPositionAsc(scheduledDaily)).thenReturn(Collections.emptyList());
        when(dailyAwardRepository.findByDaily(scheduledDaily)).thenReturn(Optional.empty());

        DailyDetailDTO result = dailyService.populateFromMessage(100L, request, "admin@example.com");

        verify(dailyResultsService).populateFromMessage(100L, request, "admin@example.com");
        assertThat(result).isNotNull();
    }

    @Test
    void populateFromMessage_propagatesException() {
        com.futspring.backend.dto.PopulateDailyRequestDTO request = new com.futspring.backend.dto.PopulateDailyRequestDTO();
        doThrow(new AppException(HttpStatus.FORBIDDEN, "Only admins can populate"))
                .when(dailyResultsService).populateFromMessage(100L, request, "member@example.com");

        assertThatThrownBy(() -> dailyService.populateFromMessage(100L, request, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
