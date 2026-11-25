package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.notification.NotificationRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.futspring.backend.domain.chat.MessageRepository;
import com.futspring.backend.domain.daily.DailyService;
import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.pelada.dto.CreatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.dto.PeladaDetailResponseDTO;
import com.futspring.backend.domain.pelada.dto.PeladaMemberDTO;
import com.futspring.backend.domain.pelada.dto.PeladaResponseDTO;
import com.futspring.backend.domain.pelada.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.stats.AggregateRebuildService;
import com.futspring.backend.domain.stats.model.RankingRepository;
import com.futspring.backend.support.MembershipStubs;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.futspring.backend.domain.pelada.invitation.PeladaInvitationService;
import com.futspring.backend.domain.pelada.member.PeladaMensalista;
import com.futspring.backend.domain.pelada.member.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStars;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStarsRepository;

@ExtendWith(MockitoExtension.class)
class PeladaServiceTest {

    @Mock
    NotificationRepository notificationRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock DailyRepository dailyRepository;
    @Mock DailyAttendanceRepository attendanceRepository;
    @Mock DailyAttendanceBumpRepository bumpRepository;
    @Mock PeladaMensalistaRepository mensalistaRepository;
    @Mock RankingRepository rankingRepository;
    @Mock MessageRepository messageRepository;
    @Mock DailyService dailyService;
    @Mock PeladaInvitationService invitationService;
    @Mock AggregateRebuildService aggregateRebuildService;
    @Mock com.futspring.backend.domain.subscription.SubscriptionService subscriptionService;
    @Mock
    PeladaRepository peladaRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    FileUploadService fileUploadService;

    @Mock
    UserAuthenticationHelper userAuthHelper;

    @Mock
    PeladaPlayerStarsRepository playerStarsRepository;

    PeladaService peladaService;

    User admin;
    User member;
    User outsider;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        com.futspring.backend.domain.subscription.SubscriptionGuard subscriptionGuard =
                mock(com.futspring.backend.domain.subscription.SubscriptionGuard.class);
        peladaService = new PeladaService(mock(com.futspring.backend.domain.finance.FinancePreservation.class), peladaRepository, playerStarsRepository, mensalistaRepository, userRepository, dailyRepository, rankingRepository, messageRepository,
                fileUploadService, userAuthHelper, new PeladaAccessHelper(peladaRepository), dailyService, aggregateRebuildService,
                notificationRepository, invitationService, subscriptionService, subscriptionGuard,
                new PeladaSummaryAssembler(peladaRepository, dailyRepository, attendanceRepository, bumpRepository,
                        mensalistaRepository, subscriptionGuard, mock(com.futspring.backend.domain.finance.FinancePreservation.class), java.time.Clock.fixed(java.time.Instant.parse("2026-11-15T01:00:00Z"), java.time.ZoneOffset.UTC)));

        admin = User.builder().emailVerifiedAt(java.time.Instant.EPOCH).id(1L).email("admin@example.com").username("admin").password("hash").build();
        member = User.builder().emailVerifiedAt(java.time.Instant.EPOCH).id(2L).email("member@example.com").username("member").password("hash").build();
        outsider = User.builder().emailVerifiedAt(java.time.Instant.EPOCH).id(3L).email("outsider@example.com").username("outsider").password("hash").build();

        Set<User> members = new HashSet<>(Set.of(admin, member));
        Set<User> admins = new HashSet<>(Set.of(admin));

        pelada = Pelada.builder()
                .id(10L)
                .name("Sunday Pelada")
                .dayOfWeek("SUNDAY")
                .timeOfDay("18:00")
                .duration(2.0f)
                .numberOfTeams(2)
                .playersPerTeam(5)
                .creator(admin)
                .members(members)
                .admins(admins)
                .build();
        MembershipStubs.stubMembership(peladaRepository, pelada);
        lenient().when(userRepository.findAllById(anyIterable())).thenAnswer(inv -> {
            Set<Long> ids = new HashSet<>();
            inv.<Iterable<Long>>getArgument(0).forEach(ids::add);
            return Stream.of(admin, member, outsider).filter(u -> ids.contains(u.getId())).toList();
        });
    }

    // --- createPelada ---

    @Test
    void createPelada_success_returnsDTO() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.save(any(Pelada.class))).thenAnswer(inv -> {
            Pelada p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 10L);
            return p;
        });

        CreatePeladaRequestDTO req = new CreatePeladaRequestDTO("New Pelada", "FRIDAY", "20:00", 1.5f, null, null, false, 2, 5, null, 0);

        PeladaResponseDTO result = peladaService.createPelada(req, "admin@example.com");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("New Pelada");
    }

    @Test
    void createPelada_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        CreatePeladaRequestDTO req = new CreatePeladaRequestDTO("Pelada", "FRIDAY", "20:00", 1.5f, null, null, false, 2, 5, null, 0);

        assertThatThrownBy(() -> peladaService.createPelada(req, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createPelada_creatorIsAddedAsMemberAndAdmin() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.save(any(Pelada.class))).thenAnswer(inv -> {
            Pelada p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 11L);
            return p;
        });

        CreatePeladaRequestDTO req = new CreatePeladaRequestDTO("Pelada", "MONDAY", "19:00", 2.0f, null, null, false, 2, 5, null, 0);

        peladaService.createPelada(req, "admin@example.com");

        verify(peladaRepository).save(argThat(p ->
                p.getMembers().contains(admin) && p.getAdmins().contains(admin)));
    }

    // --- getMyPeladas ---

    @Test
    void getMyPeladas_success_returnsUserPeladas() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByMembersContaining(admin)).thenReturn(List.of(pelada));

        List<PeladaResponseDTO> result = peladaService.getMyPeladas("admin@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Sunday Pelada");
    }

    @Test
    void getMyPeladas_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> peladaService.getMyPeladas("ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getMyPeladas_noPeladas_returnsEmptyList() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByMembersContaining(admin)).thenReturn(Collections.emptyList());

        List<PeladaResponseDTO> result = peladaService.getMyPeladas("admin@example.com");

        assertThat(result).isEmpty();
    }

    // --- getPeladaDetail ---

    @Test
    void getMyPeladas_groupsNextSessionReadsByEachPeladasLocalDay() {
        Pelada west = pelada;
        west.setTimeZone("America/Sao_Paulo");
        Pelada sameDay = Pelada.builder().id(11L).name("West too").timeZone("America/Recife").build();
        Pelada east = Pelada.builder().id(12L).name("East").timeZone("Pacific/Kiritimati").build();
        when(userAuthHelper.getAuthenticatedUser(admin.getEmail())).thenReturn(admin);
        when(peladaRepository.findByMembersContaining(admin)).thenReturn(List.of(west, sameDay, east));
        Daily nextWest = Daily.builder().id(100L).pelada(west).dailyDate(java.time.LocalDate.of(2026, 11, 14))
                .dailyTime("23:00").status(com.futspring.backend.domain.daily.DailyStatus.SCHEDULED).build();
        Daily nextEast = Daily.builder().id(101L).pelada(east).dailyDate(java.time.LocalDate.of(2026, 11, 15))
                .dailyTime("23:00").status(com.futspring.backend.domain.daily.DailyStatus.CONFIRMED).build();
        when(dailyRepository.findNextDailies(eq(List.of(10L, 11L)), any(), eq(java.time.LocalDate.of(2026, 11, 14))))
                .thenReturn(List.of(nextWest));
        when(dailyRepository.findNextDailies(eq(List.of(12L)), any(), eq(java.time.LocalDate.of(2026, 11, 15))))
                .thenReturn(List.of(nextEast));

        List<PeladaResponseDTO> result = peladaService.getMyPeladas(admin.getEmail());

        assertThat(result.get(0).nextDaily().id()).isEqualTo(100L);
        assertThat(result.get(1).nextDaily()).isNull();
        assertThat(result.get(2).nextDaily().id()).isEqualTo(101L);
        verify(dailyRepository, times(2)).findNextDailies(any(), any(), any());
    }

    @Test
    void getPeladaDetail_success_returnsDTO() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        PeladaDetailResponseDTO result = peladaService.getPeladaDetail(10L, "member@example.com");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Sunday Pelada");
    }

    @Test
    void getPeladaDetail_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> peladaService.getPeladaDetail(10L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPeladaDetail_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.getPeladaDetail(999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPeladaDetail_callerNotMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("outsider@example.com")).thenReturn(outsider);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.getPeladaDetail(10L, "outsider@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- updatePelada ---

    @Test
    void updatePelada_success_updatesFields() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdatePeladaRequestDTO req = new UpdatePeladaRequestDTO("Updated Name", "MONDAY", null, null, null, null, null, null, null, null, null);

        PeladaResponseDTO result = peladaService.updatePelada(10L, req, "admin@example.com");

        assertThat(result.name()).isEqualTo("Updated Name");
    }

    @Test
    void updatePelada_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        UpdatePeladaRequestDTO req = new UpdatePeladaRequestDTO("Hacked Name", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> peladaService.updatePelada(10L, req, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updatePelada_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.updatePelada(999L, UpdatePeladaRequestDTO.builder().build(), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updatePelada_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> peladaService.updatePelada(10L, UpdatePeladaRequestDTO.builder().build(), "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updatePelada_nullFields_noChange() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdatePeladaRequestDTO req = new UpdatePeladaRequestDTO(null, null, null, null, null, null, null, null, null, null, null);
        // all null — should not change anything

        PeladaResponseDTO result = peladaService.updatePelada(10L, req, "admin@example.com");

        assertThat(result.name()).isEqualTo("Sunday Pelada");
    }

    // --- deletePelada ---

    @Test
    void deletePelada_success_deletesPelada() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        pelada.setImage("cover.jpg");

        peladaService.deletePelada(10L, "admin@example.com");

        verify(peladaRepository).delete(pelada);
        // the sessions' champion photos go through DailyService.deleteDailyData (DailyServiceTest)
        verify(fileUploadService).deleteImageAfterCommit("cover.jpg");
    }

    @Test
    void deletePelada_cascadesDailiesMessagesRankingsAndRebuildsStats() {
        Daily d1 = Daily.builder().id(100L).pelada(pelada).dailyDate(java.time.LocalDate.now()).dailyTime("18:00").build();
        Daily d2 = Daily.builder().id(101L).pelada(pelada).dailyDate(java.time.LocalDate.now()).dailyTime("18:00").build();
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(dailyRepository.findByPelada(pelada)).thenReturn(List.of(d1, d2));
        when(dailyService.deleteDailyData(d1)).thenReturn(Set.of(admin));
        when(dailyService.deleteDailyData(d2)).thenReturn(Set.of(member));

        peladaService.deletePelada(10L, "admin@example.com");

        verify(dailyService).deleteDailyData(d1);
        verify(dailyService).deleteDailyData(d2);
        verify(messageRepository).deleteByPelada(pelada);
        verify(rankingRepository).deleteByPelada(pelada);
        verify(mensalistaRepository).deleteByPelada(pelada);
        verify(notificationRepository).deleteByPelada(pelada);
        verify(peladaRepository).delete(pelada);
        verify(aggregateRebuildService).rebuild(isNull(), eq(new java.util.LinkedHashSet<>(List.of(admin, member))));
    }

    @Test
    void deletePelada_callerNotCreator_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.deletePelada(10L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void deletePelada_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.deletePelada(999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deletePelada_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> peladaService.deletePelada(10L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- time zone and mensalista limit ---

    @Test
    void createPelada_withoutTimeZone_usesSaoPaulo_invalidZone_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.save(any(Pelada.class))).thenAnswer(inv -> inv.getArgument(0));
        CreatePeladaRequestDTO req = new CreatePeladaRequestDTO("P", "FRIDAY", "20:00", 1f, null, null, false, 2, 5, null, 0);

        peladaService.createPelada(req, "admin@example.com");
        verify(peladaRepository).save(argThat(p -> p.getTimeZone().equals("America/Sao_Paulo") && p.getMaxMensalistas() == 0));

        CreatePeladaRequestDTO invalid = new CreatePeladaRequestDTO(
                "P", "FRIDAY", "20:00", 1f, null, null, false, 2, 5, "-03:00", 0);
        assertThatThrownBy(() -> peladaService.createPelada(invalid, "admin@example.com"))
                .hasMessage("Fuso horário inválido")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updatePelada_timeZoneAndLimit_areSaved() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(pelada)).thenReturn(pelada);
        when(mensalistaRepository.countByPelada(pelada)).thenReturn(3L);

        peladaService.updatePelada(10L, UpdatePeladaRequestDTO.builder()
                .timeZone("America/Toronto").maxMensalistas(10).build(), "admin@example.com");

        assertThat(pelada.getTimeZone()).isEqualTo("America/Toronto");
        assertThat(pelada.getMaxMensalistas()).isEqualTo(10);
    }

    @Test
    void updatePelada_limitBelowTheCurrentMensalistas_throwsConflict() {
        pelada.setMaxMensalistas(5);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(mensalistaRepository.countByPelada(pelada)).thenReturn(5L);

        assertThatThrownBy(() -> peladaService.updatePelada(10L, UpdatePeladaRequestDTO.builder().maxMensalistas(3).build(),
                "admin@example.com"))
                .hasMessage("Desmarque 2 mensalistas antes de reduzir o limite")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(pelada.getMaxMensalistas()).isEqualTo(5);
    }

    @Test
    void updatePelada_limitAboveTheCapacity_throwsConflict() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.updatePelada(10L, UpdatePeladaRequestDTO.builder().maxMensalistas(11).build(),
                "admin@example.com"))
                .hasMessage("O limite de mensalistas não pode passar das vagas (10)")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updatePelada_capacityAndLimitTogether_checksTheLimitAgainstTheNewCapacity() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(pelada)).thenReturn(pelada);
        when(mensalistaRepository.countByPelada(pelada)).thenReturn(0L);

        // 3 × 6 = 18 spots: a limit of 15 only fits the new capacity
        peladaService.updatePelada(10L, UpdatePeladaRequestDTO.builder()
                .numberOfTeams(3).playersPerTeam(6).maxMensalistas(15).build(), "admin@example.com");

        assertThat(pelada.getNumberOfTeams()).isEqualTo(3);
        assertThat(pelada.getPlayersPerTeam()).isEqualTo(6);
        assertThat(pelada.getMaxMensalistas()).isEqualTo(15);
    }

    @Test
    void updatePelada_capacityBelowTheCurrentLimit_throwsConflictAndChangesNothing() {
        pelada.setMaxMensalistas(8);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.updatePelada(10L, UpdatePeladaRequestDTO.builder().playersPerTeam(3).build(),
                "admin@example.com"))
                .hasMessage("O limite de mensalistas não pode passar das vagas (6)")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(peladaRepository, never()).save(any());
    }

    @Test
    void updatePelada_capacityBelowTheConfirmedPlayers_isAllowed() {
        // Upcoming sessions are not read: a session with more confirmed players just sits over the limit
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(pelada)).thenReturn(pelada);

        peladaService.updatePelada(10L, UpdatePeladaRequestDTO.builder().numberOfTeams(2).playersPerTeam(2).build(),
                "admin@example.com");

        assertThat(pelada.capacity()).isEqualTo(4);
        verifyNoInteractions(attendanceRepository, dailyService);
    }

    @Test
    void updatePelada_invalidTimeZone_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.updatePelada(10L, UpdatePeladaRequestDTO.builder().timeZone("Mars/Olympus").build(),
                "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(pelada.getTimeZone()).isEqualTo("America/Sao_Paulo");
    }
}
