package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.notification.NotificationRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.futspring.backend.domain.chat.MessageRepository;
import com.futspring.backend.domain.daily.DailyService;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
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
import com.futspring.backend.domain.stats.repository.RankingRepository;
import com.futspring.backend.support.MembershipStubs;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
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
        peladaService = new PeladaService(mock(com.futspring.backend.domain.finance.FinancePreservation.class), peladaRepository, playerStarsRepository, mensalistaRepository, userRepository, dailyRepository, attendanceRepository, bumpRepository, rankingRepository, messageRepository,
                fileUploadService, userAuthHelper, new PeladaAccessHelper(peladaRepository), dailyService, aggregateRebuildService,
                notificationRepository, eventPublisher, invitationService);

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").build();
        member = User.builder().id(2L).email("member@example.com").username("member").password("hash").build();
        outsider = User.builder().id(3L).email("outsider@example.com").username("outsider").password("hash").build();

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

    // --- removePlayer ---

    @Test
    void removePlayer_success_removesMemberFromPelada() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        peladaService.removePlayer(10L, 2L, "admin@example.com");

        assertThat(pelada.getMembers()).doesNotContain(member);
        verify(playerStarsRepository).deleteByPeladaAndUserIn(pelada, List.of(member));
        verify(mensalistaRepository).deleteByPeladaAndUserIn(pelada, List.of(member));
        verify(dailyService).removeFromUpcomingSessions(pelada, List.of(member));
        verify(eventPublisher).publishEvent(new MembersRemovedEvent(pelada, admin, List.of(member)));
    }

    @Test
    void removePlayer_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.removePlayer(10L, 3L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void removePlayer_targetNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.removePlayer(10L, 999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void removePlayer_creatorCannotBeRemoved_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.removePlayer(10L, 1L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void removePlayer_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.removePlayer(999L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void removePlayer_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> peladaService.removePlayer(10L, 2L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- setAdmin ---

    @Test
    void setAdmin_promote_success() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        peladaService.setAdmin(10L, 2L, true, "admin@example.com");

        assertThat(pelada.getAdmins()).contains(member);
        verify(eventPublisher).publishEvent(new AdminsChangedEvent(pelada, admin, List.of(member), true));
    }

    @Test
    void setAdmins_onlyMembersWhoseStatusChangesAreNotified() {
        pelada.getMembers().add(outsider);
        pelada.getAdmins().add(member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        peladaService.setAdmins(10L, List.of(2L, 3L), true, "admin@example.com");

        verify(eventPublisher).publishEvent(new AdminsChangedEvent(pelada, admin, List.of(outsider), true));
    }

    @Test
    void setAdmin_demote_success() {
        pelada.getAdmins().add(member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        peladaService.setAdmin(10L, 2L, false, "admin@example.com");

        assertThat(pelada.getAdmins()).doesNotContain(member);
        verify(eventPublisher).publishEvent(new AdminsChangedEvent(pelada, admin, List.of(member), false));
    }

    @Test
    void setAdmin_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setAdmin(10L, 3L, true, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void setAdmin_targetIsCreator_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setAdmin(10L, 1L, false, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void setAdmin_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.setAdmin(999L, 2L, true, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void setAdmin_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> peladaService.setAdmin(10L, 2L, true, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void setAdmin_targetNotMember_throwsNotFoundAndKeepsAdmins() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setAdmin(10L, 3L, true, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(pelada.getAdmins()).doesNotContain(outsider);
        verify(peladaRepository, never()).saveAndFlush(any());
    }

    @Test
    void setAdmin_targetNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setAdmin(10L, 999L, true, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- setPlayerStars ---

    @Test
    void setPlayerStars_newRating_createsRow() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(playerStarsRepository.findByPeladaAndUserIn(pelada, List.of(member))).thenReturn(List.of());
        when(playerStarsRepository.findStarsByPelada(pelada)).thenReturn(java.util.Map.of(2L, 5));

        PeladaMemberDTO result = peladaService.setPlayerStars(10L, 2L, 5, "admin@example.com");

        verify(playerStarsRepository).saveAll(argThat((List<PeladaPlayerStars> rows) -> rows.size() == 1
                && rows.get(0).getPelada() == pelada && rows.get(0).getUser() == member && rows.get(0).getStars() == 5));
        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.stars()).isEqualTo(5);
        assertThat(result.isAdmin()).isFalse();
    }

    @Test
    void setPlayerStars_existingRating_updatesIt() {
        PeladaPlayerStars existing = PeladaPlayerStars.builder().id(7L).pelada(pelada).user(member).stars(4).build();
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(playerStarsRepository.findByPeladaAndUserIn(pelada, List.of(member))).thenReturn(List.of(existing));

        peladaService.setPlayerStars(10L, 2L, 1, "admin@example.com");

        verify(playerStarsRepository).saveAll(List.of(existing));
        assertThat(existing.getStars()).isEqualTo(1);
    }

    @Test
    void setPlayerStars_adminRatesThemselves() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(playerStarsRepository.findByPeladaAndUserIn(pelada, List.of(admin))).thenReturn(List.of());
        when(playerStarsRepository.findStarsByPelada(pelada)).thenReturn(java.util.Map.of(1L, 5));

        PeladaMemberDTO result = peladaService.setPlayerStars(10L, 1L, 5, "admin@example.com");

        assertThat(result.stars()).isEqualTo(5);
        assertThat(result.isAdmin()).isTrue();
    }

    @Test
    void setPlayerStars_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setPlayerStars(10L, 1L, 5, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(playerStarsRepository, never()).saveAll(any());
    }

    @Test
    void setPlayerStars_callerNotMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("outsider@example.com")).thenReturn(outsider);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setPlayerStars(10L, 2L, 5, "outsider@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void setPlayerStars_targetNotMember_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setPlayerStars(10L, 3L, 5, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(playerStarsRepository, never()).saveAll(any());
    }

    // --- searchUsers ---

    @Test
    void searchUsers_returnsMatchingUsers() {
        when(userRepository.searchByUsernameOrEmail(eq("ali"), any())).thenReturn(List.of(admin));

        List<PublicUserDTO> result = peladaService.searchUsers("ali");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).username()).isEqualTo("admin");
    }

    @Test
    void searchUsers_queryTooShort_throwsBadRequest() {
        assertThatThrownBy(() -> peladaService.searchUsers(" a "))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchUsers_noMatches_returnsEmptyList() {
        when(userRepository.searchByUsernameOrEmail(eq("zzz"), any())).thenReturn(Collections.emptyList());

        List<PublicUserDTO> result = peladaService.searchUsers("zzz");

        assertThat(result).isEmpty();
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

        peladaService.deletePelada(10L, "admin@example.com");

        verify(peladaRepository).delete(pelada);
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

    // --- setMensalista ---

    @Test
    void setMensalista_marksAMemberWithinTheLimit() {
        pelada.setMaxMensalistas(2);
        stubSetMensalista(admin, member);
        stubMensalistas(4L);

        PeladaMemberDTO result = peladaService.setMensalista(10L, 2L, true, "admin@example.com");

        verify(mensalistaRepository).saveAll(argThat((List<PeladaMensalista> rows) -> rows.size() == 1
                && rows.get(0).getPelada() == pelada && rows.get(0).getUser() == member));
        assertThat(result.isMensalista()).isTrue();
        assertThat(result.stars()).isEqualTo(PeladaPlayerStars.DEFAULT_STARS);
    }

    @Test
    void setMensalista_limitFull_throwsConflict() {
        pelada.setMaxMensalistas(2);
        stubSetMensalista(admin, member);
        stubMensalistas(4L, 5L);

        assertThatThrownBy(() -> peladaService.setMensalista(10L, 2L, true, "admin@example.com"))
                .hasMessage("Limite de mensalistas atingido (2/2)")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(mensalistaRepository, never()).saveAll(any());
    }

    @Test
    void setMensalista_alreadyMensalista_isIdempotentEvenWithTheLimitFull() {
        pelada.setMaxMensalistas(1);
        stubSetMensalista(admin, member);
        stubMensalistas(2L);

        PeladaMemberDTO result = peladaService.setMensalista(10L, 2L, true, "admin@example.com");

        assertThat(result.isMensalista()).isTrue();
        verify(mensalistaRepository).saveAll(List.of());
    }

    @Test
    void setMensalista_unmark_deletesTheRow() {
        stubSetMensalista(admin, member);
        stubMensalistas(2L);

        PeladaMemberDTO result = peladaService.setMensalista(10L, 2L, false, "admin@example.com");

        verify(mensalistaRepository).deleteByPeladaAndUserIn(pelada, List.of(member));
        assertThat(result.isMensalista()).isFalse();
    }

    @Test
    void setMensalista_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setMensalista(10L, 2L, true, "member@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(mensalistaRepository);
    }

    @Test
    void setMensalista_targetNotAMember_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setMensalista(10L, 3L, true, "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(mensalistaRepository);
    }

    // --- bulk member actions ---

    @Test
    void setAdmins_promotesEveryTarget_andReturnsThem() {
        pelada.getMembers().add(outsider);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        List<PeladaMemberDTO> result = peladaService.setAdmins(10L, List.of(2L, 3L), true, "admin@example.com");

        assertThat(pelada.getAdmins()).contains(member, outsider);
        assertThat(result).extracting(PeladaMemberDTO::id).containsExactly(2L, 3L);
        assertThat(result).allMatch(PeladaMemberDTO::isAdmin);
    }

    @Test
    void setAdmins_oneTargetNotAMember_changesNobody() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setAdmins(10L, List.of(2L, 3L), true, "admin@example.com"))
                .hasMessage("outsider não é membro desta pelada")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(pelada.getAdmins()).containsExactly(admin);
        verify(peladaRepository, never()).saveAndFlush(any());
    }

    @Test
    void setAdmins_creatorInTheBatch_forbiddenAndNobodyChanges() {
        pelada.getAdmins().add(member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setAdmins(10L, List.of(2L, 1L), false, "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(pelada.getAdmins()).containsExactlyInAnyOrder(admin, member);
        verify(peladaRepository, never()).saveAndFlush(any());
    }

    @Test
    void setMensalistas_batchOverTheLimit_conflictAndNobodyChanges() {
        pelada.getMembers().add(outsider);
        pelada.setMaxMensalistas(2);
        stubSetMensalista(admin, member);
        Set<Long> mensalistas = stubMensalistas(4L);

        assertThatThrownBy(() -> peladaService.setMensalistas(10L, List.of(2L, 3L), true, "admin@example.com"))
                .hasMessage("Limite de mensalistas atingido: cabem 1, foram selecionados 2")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(mensalistaRepository, never()).saveAll(any());
        assertThat(mensalistas).containsExactly(4L);
    }

    @Test
    void setMensalistas_onlyMembersWhoChangeCountTowardsTheLimit() {
        pelada.getMembers().add(outsider);
        pelada.setMaxMensalistas(2);
        stubSetMensalista(admin, member);
        Set<Long> mensalistas = stubMensalistas(2L);

        List<PeladaMemberDTO> result = peladaService.setMensalistas(10L, List.of(2L, 3L), true, "admin@example.com");

        assertThat(mensalistas).containsExactlyInAnyOrder(2L, 3L);
        assertThat(result).allMatch(PeladaMemberDTO::isMensalista);
        // member 2 already was one: only the outsider is notified
        verify(eventPublisher).publishEvent(new MensalistasChangedEvent(pelada, admin, List.of(outsider), true));
    }

    @Test
    void setMensalistas_unmark_deletesOnlyTheMensalistas() {
        pelada.getMembers().add(outsider);
        stubSetMensalista(admin, member);
        stubMensalistas(2L);

        List<PeladaMemberDTO> result = peladaService.setMensalistas(10L, List.of(2L, 3L), false, "admin@example.com");

        verify(mensalistaRepository).deleteByPeladaAndUserIn(pelada, List.of(member));
        assertThat(result).noneMatch(PeladaMemberDTO::isMensalista);
        verify(eventPublisher).publishEvent(new MensalistasChangedEvent(pelada, admin, List.of(member), false));
    }

    @Test
    void setPlayersStars_updatesExistingRowsAndCreatesTheMissingOnes() {
        PeladaPlayerStars existing = PeladaPlayerStars.builder().id(7L).pelada(pelada).user(member).stars(2).build();
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(playerStarsRepository.findByPeladaAndUserIn(pelada, List.of(admin, member))).thenReturn(List.of(existing));
        when(playerStarsRepository.findStarsByPelada(pelada)).thenReturn(java.util.Map.of(1L, 4, 2L, 4));

        List<PeladaMemberDTO> result = peladaService.setPlayersStars(10L, List.of(1L, 2L), 4, "admin@example.com");

        verify(playerStarsRepository).saveAll(argThat((List<PeladaPlayerStars> rows) -> rows.size() == 2
                && rows.get(0).getUser() == admin && rows.get(0).getId() == null
                && rows.get(1) == existing && rows.stream().allMatch(r -> r.getStars() == 4)));
        assertThat(result).extracting(PeladaMemberDTO::stars).containsExactly(4, 4);
    }

    @Test
    void setPlayersStars_oneTargetNotAMember_savesNothing() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setPlayersStars(10L, List.of(2L, 999L), 4, "admin@example.com"))
                .hasMessage("O jogador não é membro desta pelada")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(playerStarsRepository);
    }

    @Test
    void removePlayers_removesEveryTargetWithBatchSideEffects() {
        pelada.getMembers().add(outsider);
        pelada.getAdmins().add(outsider);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        peladaService.removePlayers(10L, List.of(2L, 3L), "admin@example.com");

        assertThat(pelada.getMembers()).containsExactly(admin);
        assertThat(pelada.getAdmins()).containsExactly(admin);
        verify(invitationService).recordRemoval(pelada, List.of(member, outsider));
        verify(playerStarsRepository).deleteByPeladaAndUserIn(pelada, List.of(member, outsider));
        verify(mensalistaRepository).deleteByPeladaAndUserIn(pelada, List.of(member, outsider));
        verify(dailyService).removeFromUpcomingSessions(pelada, List.of(member, outsider));
        verify(peladaRepository).save(pelada);
    }

    @Test
    void removePlayers_creatorInTheBatch_forbiddenAndNothingChanges() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.removePlayers(10L, List.of(2L, 1L), "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(pelada.getMembers()).containsExactlyInAnyOrder(admin, member);
        verifyNoInteractions(playerStarsRepository, mensalistaRepository, dailyService);
    }

    @Test
    void removePlayers_oneTargetNotAMember_nothingChanges() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.removePlayers(10L, List.of(2L, 3L), "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(pelada.getMembers()).containsExactlyInAnyOrder(admin, member);
        verifyNoInteractions(playerStarsRepository, mensalistaRepository, dailyService);
    }

    @Test
    void removePlayers_callerNotAdmin_forbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.removePlayers(10L, List.of(2L), "member@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(dailyService);
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

    // --- transferCreator ---

    @Test
    void transferCreator_toAnAdmin_changesTheCreatorAndKeepsTheOldOneAdmin() {
        pelada.getAdmins().add(member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(pelada)).thenReturn(pelada);

        peladaService.transferCreator(10L, member.getId(), "admin@example.com");

        assertThat(pelada.getCreator()).isEqualTo(member);
        assertThat(pelada.getAdmins()).contains(admin, member);
        assertThat(pelada.getMembers()).contains(admin);
        verify(eventPublisher).publishEvent(new PeladaTransferredEvent(pelada, admin, member));
    }

    @Test
    void transferCreator_callerIsAdminButNotCreator_throwsForbidden() {
        pelada.getAdmins().add(member);
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.transferCreator(10L, admin.getId(), "member@example.com"))
                .hasMessage("Apenas o criador pode transferir a pelada")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(pelada.getCreator()).isEqualTo(admin);
    }

    @Test
    void transferCreator_targetIsNotAdmin_throwsConflict() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.transferCreator(10L, member.getId(), "admin@example.com"))
                .hasMessage("Só é possível transferir a pelada para um admin")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(pelada.getCreator()).isEqualTo(admin);
    }

    @Test
    void transferCreator_targetIsNotAMember_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.transferCreator(10L, outsider.getId(), "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(pelada.getCreator()).isEqualTo(admin);
    }

    @Test
    void transferCreator_toYourself_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.transferCreator(10L, admin.getId(), "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
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

    private void stubSetMensalista(User caller, User target) {
        when(userAuthHelper.getAuthenticatedUser(caller.getEmail())).thenReturn(caller);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
    }

    // Backs the mensalista repository with an in-memory set of user ids, like a database would
    private Set<Long> stubMensalistas(Long... userIds) {
        Set<Long> ids = new HashSet<>(List.of(userIds));
        lenient().when(mensalistaRepository.findUserIdsByPelada(pelada)).thenAnswer(inv -> new HashSet<>(ids));
        lenient().when(mensalistaRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<PeladaMensalista> rows = inv.getArgument(0);
            rows.forEach(r -> ids.add(r.getUser().getId()));
            return rows;
        });
        lenient().doAnswer(inv -> {
            inv.<java.util.Collection<User>>getArgument(1).forEach(u -> ids.remove(u.getId()));
            return null;
        }).when(mensalistaRepository).deleteByPeladaAndUserIn(eq(pelada), anyCollection());
        return ids;
    }
}
