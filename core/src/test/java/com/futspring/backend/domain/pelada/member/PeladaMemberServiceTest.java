package com.futspring.backend.domain.pelada.member;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.pelada.member.PeladaTransferredEvent;
import com.futspring.backend.domain.pelada.member.MensalistasChangedEvent;
import com.futspring.backend.domain.pelada.member.MembersRemovedEvent;
import com.futspring.backend.domain.pelada.member.AdminsChangedEvent;
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
class PeladaMemberServiceTest {

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock PeladaMensalistaRepository mensalistaRepository;
    @Mock DailyService dailyService;
    @Mock PeladaInvitationService invitationService;
    @Mock
    PeladaRepository peladaRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    UserAuthenticationHelper userAuthHelper;

    @Mock
    PeladaPlayerStarsRepository playerStarsRepository;

    PeladaMemberService memberService;

    User admin;
    User member;
    User outsider;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        memberService = new PeladaMemberService(mock(com.futspring.backend.domain.finance.FinancePreservation.class),
                peladaRepository, playerStarsRepository, mensalistaRepository, userRepository, userAuthHelper,
                new PeladaAccessHelper(peladaRepository), dailyService, eventPublisher, invitationService);

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

    // --- removePlayer ---

    @Test
    void removePlayer_success_removesMemberFromPelada() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        memberService.removePlayer(10L, 2L, "admin@example.com");

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

        assertThatThrownBy(() -> memberService.removePlayer(10L, 3L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void removePlayer_targetNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.removePlayer(10L, 999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void removePlayer_creatorCannotBeRemoved_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.removePlayer(10L, 1L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void removePlayer_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.removePlayer(999L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void removePlayer_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> memberService.removePlayer(10L, 2L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- setAdmin ---

    @Test
    void setAdmin_promote_success() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        memberService.setAdmin(10L, 2L, true, "admin@example.com");

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

        memberService.setAdmins(10L, List.of(2L, 3L), true, "admin@example.com");

        verify(eventPublisher).publishEvent(new AdminsChangedEvent(pelada, admin, List.of(outsider), true));
    }

    @Test
    void setAdmin_demote_success() {
        pelada.getAdmins().add(member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        memberService.setAdmin(10L, 2L, false, "admin@example.com");

        assertThat(pelada.getAdmins()).doesNotContain(member);
        verify(eventPublisher).publishEvent(new AdminsChangedEvent(pelada, admin, List.of(member), false));
    }

    @Test
    void setAdmin_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setAdmin(10L, 3L, true, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void setAdmin_targetIsCreator_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setAdmin(10L, 1L, false, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void setAdmin_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.setAdmin(999L, 2L, true, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void setAdmin_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> memberService.setAdmin(10L, 2L, true, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void setAdmin_targetNotMember_throwsNotFoundAndKeepsAdmins() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setAdmin(10L, 3L, true, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(pelada.getAdmins()).doesNotContain(outsider);
        verify(peladaRepository, never()).saveAndFlush(any());
    }

    @Test
    void setAdmin_targetNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setAdmin(10L, 999L, true, "admin@example.com"))
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

        PeladaMemberDTO result = memberService.setPlayerStars(10L, 2L, 5, "admin@example.com");

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

        memberService.setPlayerStars(10L, 2L, 1, "admin@example.com");

        verify(playerStarsRepository).saveAll(List.of(existing));
        assertThat(existing.getStars()).isEqualTo(1);
    }

    @Test
    void setPlayerStars_adminRatesThemselves() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(playerStarsRepository.findByPeladaAndUserIn(pelada, List.of(admin))).thenReturn(List.of());
        when(playerStarsRepository.findStarsByPelada(pelada)).thenReturn(java.util.Map.of(1L, 5));

        PeladaMemberDTO result = memberService.setPlayerStars(10L, 1L, 5, "admin@example.com");

        assertThat(result.stars()).isEqualTo(5);
        assertThat(result.isAdmin()).isTrue();
    }

    @Test
    void setPlayerStars_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setPlayerStars(10L, 1L, 5, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(playerStarsRepository, never()).saveAll(any());
    }

    @Test
    void setPlayerStars_callerNotMember_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("outsider@example.com")).thenReturn(outsider);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setPlayerStars(10L, 2L, 5, "outsider@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void setPlayerStars_targetNotMember_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setPlayerStars(10L, 3L, 5, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(playerStarsRepository, never()).saveAll(any());
    }

    // --- setMensalista ---

    @Test
    void setMensalista_marksAMemberWithinTheLimit() {
        pelada.setMaxMensalistas(2);
        stubSetMensalista(admin, member);
        stubMensalistas(4L);

        PeladaMemberDTO result = memberService.setMensalista(10L, 2L, true, "admin@example.com");

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

        assertThatThrownBy(() -> memberService.setMensalista(10L, 2L, true, "admin@example.com"))
                .hasMessage("Limite de mensalistas atingido (2/2)")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(mensalistaRepository, never()).saveAll(any());
    }

    @Test
    void setMensalista_alreadyMensalista_isIdempotentEvenWithTheLimitFull() {
        pelada.setMaxMensalistas(1);
        stubSetMensalista(admin, member);
        stubMensalistas(2L);

        PeladaMemberDTO result = memberService.setMensalista(10L, 2L, true, "admin@example.com");

        assertThat(result.isMensalista()).isTrue();
        verify(mensalistaRepository).saveAll(List.of());
    }

    @Test
    void setMensalista_unmark_deletesTheRow() {
        stubSetMensalista(admin, member);
        stubMensalistas(2L);

        PeladaMemberDTO result = memberService.setMensalista(10L, 2L, false, "admin@example.com");

        verify(mensalistaRepository).deleteByPeladaAndUserIn(pelada, List.of(member));
        assertThat(result.isMensalista()).isFalse();
    }

    @Test
    void setMensalista_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setMensalista(10L, 2L, true, "member@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(mensalistaRepository);
    }

    @Test
    void setMensalista_targetNotAMember_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setMensalista(10L, 3L, true, "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(mensalistaRepository);
    }

    // --- bulk member actions ---

    @Test
    void setAdmins_promotesEveryTarget_andReturnsThem() {
        pelada.getMembers().add(outsider);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        List<PeladaMemberDTO> result = memberService.setAdmins(10L, List.of(2L, 3L), true, "admin@example.com");

        assertThat(pelada.getAdmins()).contains(member, outsider);
        assertThat(result).extracting(PeladaMemberDTO::id).containsExactly(2L, 3L);
        assertThat(result).allMatch(PeladaMemberDTO::isAdmin);
    }

    @Test
    void setAdmins_oneTargetNotAMember_changesNobody() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setAdmins(10L, List.of(2L, 3L), true, "admin@example.com"))
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

        assertThatThrownBy(() -> memberService.setAdmins(10L, List.of(2L, 1L), false, "admin@example.com"))
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

        assertThatThrownBy(() -> memberService.setMensalistas(10L, List.of(2L, 3L), true, "admin@example.com"))
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

        List<PeladaMemberDTO> result = memberService.setMensalistas(10L, List.of(2L, 3L), true, "admin@example.com");

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

        List<PeladaMemberDTO> result = memberService.setMensalistas(10L, List.of(2L, 3L), false, "admin@example.com");

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

        List<PeladaMemberDTO> result = memberService.setPlayersStars(10L, List.of(1L, 2L), 4, "admin@example.com");

        verify(playerStarsRepository).saveAll(argThat((List<PeladaPlayerStars> rows) -> rows.size() == 2
                && rows.get(0).getUser() == admin && rows.get(0).getId() == null
                && rows.get(1) == existing && rows.stream().allMatch(r -> r.getStars() == 4)));
        assertThat(result).extracting(PeladaMemberDTO::stars).containsExactly(4, 4);
    }

    @Test
    void setPlayersStars_oneTargetNotAMember_savesNothing() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.setPlayersStars(10L, List.of(2L, 999L), 4, "admin@example.com"))
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

        memberService.removePlayers(10L, List.of(2L, 3L), "admin@example.com");

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

        assertThatThrownBy(() -> memberService.removePlayers(10L, List.of(2L, 1L), "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(pelada.getMembers()).containsExactlyInAnyOrder(admin, member);
        verifyNoInteractions(playerStarsRepository, mensalistaRepository, dailyService);
    }

    @Test
    void removePlayers_oneTargetNotAMember_nothingChanges() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.removePlayers(10L, List.of(2L, 3L), "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(pelada.getMembers()).containsExactlyInAnyOrder(admin, member);
        verifyNoInteractions(playerStarsRepository, mensalistaRepository, dailyService);
    }

    @Test
    void removePlayers_callerNotAdmin_forbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.removePlayers(10L, List.of(2L), "member@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(dailyService);
    }

    // --- transferCreator ---

    @Test
    void transferCreator_toAnAdmin_changesTheCreatorAndKeepsTheOldOneAdmin() {
        pelada.getAdmins().add(member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(pelada)).thenReturn(pelada);

        memberService.transferCreator(10L, member.getId(), "admin@example.com");

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

        assertThatThrownBy(() -> memberService.transferCreator(10L, admin.getId(), "member@example.com"))
                .hasMessage("Apenas o criador pode transferir a pelada")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(pelada.getCreator()).isEqualTo(admin);
    }

    @Test
    void transferCreator_targetIsNotAdmin_throwsConflict() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.transferCreator(10L, member.getId(), "admin@example.com"))
                .hasMessage("Só é possível transferir a pelada para um admin")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(pelada.getCreator()).isEqualTo(admin);
    }

    @Test
    void transferCreator_targetIsNotAMember_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.transferCreator(10L, outsider.getId(), "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(pelada.getCreator()).isEqualTo(admin);
    }

    @Test
    void transferCreator_toYourself_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> memberService.transferCreator(10L, admin.getId(), "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
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
