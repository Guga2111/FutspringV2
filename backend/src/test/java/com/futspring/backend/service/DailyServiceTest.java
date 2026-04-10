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

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyServiceTest {

    @Mock FileUploadService fileUploadService;
    @Mock UserAuthenticationHelper userAuthHelper;
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
                fileUploadService, userAuthHelper,
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

    // --- confirmAttendance ---

    @Test
    void confirmAttendance_success_addsPlayer() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DailyListItemDTO result = dailyService.confirmAttendance(100L, "member@example.com");

        assertThat(scheduledDaily.getConfirmedPlayers()).contains(member);
    }

    @Test
    void confirmAttendance_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> dailyService.confirmAttendance(100L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void confirmAttendance_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyService.confirmAttendance(999L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void confirmAttendance_notMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.confirmAttendance(100L, "out@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void confirmAttendance_statusInCourse_throwsBadRequest() {
        scheduledDaily.setStatus("IN_COURSE");
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.confirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void confirmAttendance_statusFinished_throwsBadRequest() {
        scheduledDaily.setStatus("FINISHED");
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.confirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void confirmAttendance_statusCanceled_throwsBadRequest() {
        scheduledDaily.setStatus("CANCELED");
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.confirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void confirmAttendance_alreadyConfirmed_throwsConflict() {
        scheduledDaily.getConfirmedPlayers().add(member);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.confirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // --- disconfirmAttendance ---

    @Test
    void disconfirmAttendance_success_removesPlayer() {
        scheduledDaily.getConfirmedPlayers().add(member);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(dailyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dailyService.disconfirmAttendance(100L, "member@example.com");

        assertThat(scheduledDaily.getConfirmedPlayers()).doesNotContain(member);
    }

    @Test
    void disconfirmAttendance_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> dailyService.disconfirmAttendance(100L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void disconfirmAttendance_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyService.disconfirmAttendance(999L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void disconfirmAttendance_locked_throwsBadRequest() {
        scheduledDaily.setStatus("FINISHED");
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.disconfirmAttendance(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void disconfirmAttendance_notConfirmed_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

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

    // --- sortTeams ---

    @Test
    void sortTeams_success_createsTeams() {
        // 2 teams x 1 player each = need exactly 2 confirmed players
        scheduledDaily.getConfirmedPlayers().add(admin);
        scheduledDaily.getConfirmedPlayers().add(member);

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            if (t.getId() == null) ReflectionTestUtils.setField(t, "id", (long)(Math.random() * 1000));
            return t;
        });
        when(teamRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<DailyDetailDTO.TeamDTO> result = dailyService.sortTeams(100L, "admin@example.com");

        assertThat(result).hasSize(2);
    }

    @Test
    void sortTeams_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.sortTeams(100L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void sortTeams_lockedStatus_throwsBadRequest() {
        scheduledDaily.setStatus("FINISHED");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sortTeams_wrongPlayerCount_throwsBadRequest() {
        // 0 players but need 2 (2 teams x 1 player)
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> dailyService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void sortTeams_deletesExistingTeams() {
        scheduledDaily.getConfirmedPlayers().add(admin);
        scheduledDaily.getConfirmedPlayers().add(member);

        Team existingTeam = Team.builder().id(1L).daily(scheduledDaily).name("Old Team").players(new HashSet<>()).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(List.of(existingTeam));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            if (t.getId() == null) ReflectionTestUtils.setField(t, "id", (long)(Math.random() * 1000));
            return t;
        });
        when(teamRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        dailyService.sortTeams(100L, "admin@example.com");

        verify(teamRepository).deleteAll(List.of(existingTeam));
    }

    @Test
    void sortTeams_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> dailyService.sortTeams(100L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void sortTeams_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyService.sortTeams(999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void sortTeams_tooManyPlayers_throwsBadRequest() {
        // need exactly 2 (2 teams x 1), add 3
        scheduledDaily.getConfirmedPlayers().add(admin);
        scheduledDaily.getConfirmedPlayers().add(member);
        scheduledDaily.getConfirmedPlayers().add(outsider);

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> dailyService.sortTeams(100L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- swapPlayers ---

    @Test
    void swapPlayers_success_swapsPlayers() {
        Team team1 = Team.builder().id(1L).daily(scheduledDaily).name("Team 1")
                .players(new HashSet<>(Set.of(admin))).build();
        Team team2 = Team.builder().id(2L).daily(scheduledDaily).name("Team 2")
                .players(new HashSet<>(Set.of(member))).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(List.of(team1, team2));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<DailyDetailDTO.TeamDTO> result = dailyService.swapPlayers(100L, 1L, 2L, "admin@example.com");

        assertThat(team1.getPlayers()).contains(member);
        assertThat(team2.getPlayers()).contains(admin);
    }

    @Test
    void swapPlayers_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.swapPlayers(100L, 1L, 2L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void swapPlayers_lockedStatus_throwsBadRequest() {
        scheduledDaily.setStatus("FINISHED");
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));

        assertThatThrownBy(() -> dailyService.swapPlayers(100L, 1L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void swapPlayers_player1NotOnTeam_throwsBadRequest() {
        Team team2 = Team.builder().id(2L).daily(scheduledDaily).name("Team 2")
                .players(new HashSet<>(Set.of(member))).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(List.of(team2));

        assertThatThrownBy(() -> dailyService.swapPlayers(100L, 999L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void swapPlayers_player2NotOnTeam_throwsBadRequest() {
        Team team1 = Team.builder().id(1L).daily(scheduledDaily).name("Team 1")
                .players(new HashSet<>(Set.of(admin))).build();

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(100L)).thenReturn(Optional.of(scheduledDaily));
        when(teamRepository.findByDaily(scheduledDaily)).thenReturn(List.of(team1));

        assertThatThrownBy(() -> dailyService.swapPlayers(100L, 1L, 999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void swapPlayers_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> dailyService.swapPlayers(100L, 1L, 2L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void swapPlayers_dailyNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(dailyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dailyService.swapPlayers(999L, 1L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
