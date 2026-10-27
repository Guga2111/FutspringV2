package com.futspring.backend.domain.pelada;

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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeladaServiceTest {

    @Mock DailyRepository dailyRepository;
    @Mock DailyAttendanceRepository attendanceRepository;
    @Mock DailyAttendanceBumpRepository bumpRepository;
    @Mock PeladaMensalistaRepository mensalistaRepository;
    @Mock RankingRepository rankingRepository;
    @Mock MessageRepository messageRepository;
    @Mock DailyService dailyService;
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
        peladaService = new PeladaService(peladaRepository, playerStarsRepository, mensalistaRepository, userRepository, dailyRepository, attendanceRepository, bumpRepository, rankingRepository, messageRepository,
                fileUploadService, userAuthHelper, new PeladaAccessHelper(peladaRepository), dailyService, aggregateRebuildService);

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

        CreatePeladaRequestDTO req = new CreatePeladaRequestDTO();
        req.setName("New Pelada");
        req.setDayOfWeek("FRIDAY");
        req.setTimeOfDay("20:00");
        req.setDuration(1.5f);
        req.setNumberOfTeams(2);
        req.setPlayersPerTeam(5);

        PeladaResponseDTO result = peladaService.createPelada(req, "admin@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Pelada");
    }

    @Test
    void createPelada_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        CreatePeladaRequestDTO req = new CreatePeladaRequestDTO();
        req.setName("Pelada");
        req.setDayOfWeek("FRIDAY");
        req.setTimeOfDay("20:00");
        req.setDuration(1.5f);

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

        CreatePeladaRequestDTO req = new CreatePeladaRequestDTO();
        req.setName("Pelada");
        req.setDayOfWeek("MONDAY");
        req.setTimeOfDay("19:00");
        req.setDuration(2.0f);

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
        assertThat(result.get(0).getName()).isEqualTo("Sunday Pelada");
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
        assertThat(result.getName()).isEqualTo("Sunday Pelada");
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

    // --- addPlayer ---

    @Test
    void addPlayer_success_addsMemberToPelada() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        peladaService.addPlayer(10L, 3L, "admin@example.com");

        assertThat(pelada.getMembers()).contains(outsider);
    }

    @Test
    void addPlayer_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> peladaService.addPlayer(10L, 3L, "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void addPlayer_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.addPlayer(10L, 3L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void addPlayer_targetNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.addPlayer(10L, 999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void addPlayer_alreadyMember_throwsConflict() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> peladaService.addPlayer(10L, 2L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void addPlayer_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.addPlayer(999L, 3L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- removePlayer ---

    @Test
    void removePlayer_success_removesMemberFromPelada() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        peladaService.removePlayer(10L, 2L, "admin@example.com");

        assertThat(pelada.getMembers()).doesNotContain(member);
        verify(playerStarsRepository).deleteByPeladaAndUser(pelada, member);
        verify(mensalistaRepository).deleteByPeladaAndUser(pelada, member);
        verify(dailyService).removeFromUpcomingSessions(pelada, member);
    }

    @Test
    void removePlayer_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.removePlayer(10L, 3L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void removePlayer_targetNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.removePlayer(10L, 999L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void removePlayer_creatorCannotBeRemoved_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> peladaService.removePlayer(10L, 1L, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void removePlayer_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

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
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        peladaService.setAdmin(10L, 2L, true, "admin@example.com");

        assertThat(pelada.getAdmins()).contains(member);
    }

    @Test
    void setAdmin_demote_success() {
        pelada.getAdmins().add(member);
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        peladaService.setAdmin(10L, 2L, false, "admin@example.com");

        assertThat(pelada.getAdmins()).doesNotContain(member);
    }

    @Test
    void setAdmin_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setAdmin(10L, 3L, true, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void setAdmin_targetIsCreator_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> peladaService.setAdmin(10L, 1L, false, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void setAdmin_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

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
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> peladaService.setAdmin(10L, 3L, true, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(pelada.getAdmins()).doesNotContain(outsider);
        verify(peladaRepository, never()).save(any());
    }

    @Test
    void setAdmin_targetNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.setAdmin(10L, 999L, true, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- setPlayerStars ---

    @Test
    void setPlayerStars_newRating_createsRow() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(playerStarsRepository.findByPeladaAndUser(pelada, member)).thenReturn(Optional.empty());

        PeladaMemberDTO result = peladaService.setPlayerStars(10L, 2L, 5, "admin@example.com");

        verify(playerStarsRepository).save(argThat(s ->
                s.getPelada() == pelada && s.getUser() == member && s.getStars() == 5));
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getStars()).isEqualTo(5);
        assertThat(result.isAdmin()).isFalse();
    }

    @Test
    void setPlayerStars_existingRating_updatesIt() {
        PeladaPlayerStars existing = PeladaPlayerStars.builder().id(7L).pelada(pelada).user(member).stars(4).build();
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(playerStarsRepository.findByPeladaAndUser(pelada, member)).thenReturn(Optional.of(existing));

        peladaService.setPlayerStars(10L, 2L, 1, "admin@example.com");

        verify(playerStarsRepository).save(existing);
        assertThat(existing.getStars()).isEqualTo(1);
    }

    @Test
    void setPlayerStars_adminRatesThemselves() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(playerStarsRepository.findByPeladaAndUser(pelada, admin)).thenReturn(Optional.empty());

        PeladaMemberDTO result = peladaService.setPlayerStars(10L, 1L, 5, "admin@example.com");

        assertThat(result.getStars()).isEqualTo(5);
        assertThat(result.isAdmin()).isTrue();
    }

    @Test
    void setPlayerStars_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.setPlayerStars(10L, 1L, 5, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(playerStarsRepository, never()).save(any());
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
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> peladaService.setPlayerStars(10L, 3L, 5, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(playerStarsRepository, never()).save(any());
    }

    // --- searchUsers ---

    @Test
    void searchUsers_returnsMatchingUsers() {
        when(userRepository.searchByUsernameOrEmail(eq("ali"), any())).thenReturn(List.of(admin));

        List<PublicUserDTO> result = peladaService.searchUsers("ali");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("admin");
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

        UpdatePeladaRequestDTO req = new UpdatePeladaRequestDTO();
        req.setName("Updated Name");
        req.setDayOfWeek("MONDAY");

        PeladaResponseDTO result = peladaService.updatePelada(10L, req, "admin@example.com");

        assertThat(result.getName()).isEqualTo("Updated Name");
    }

    @Test
    void updatePelada_callerNotAdmin_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        UpdatePeladaRequestDTO req = new UpdatePeladaRequestDTO();
        req.setName("Hacked Name");

        assertThatThrownBy(() -> peladaService.updatePelada(10L, req, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updatePelada_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.updatePelada(999L, new UpdatePeladaRequestDTO(), "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updatePelada_callerNotFound_throwsUnauthorized() {
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> peladaService.updatePelada(10L, new UpdatePeladaRequestDTO(), "ghost@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updatePelada_nullFields_noChange() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdatePeladaRequestDTO req = new UpdatePeladaRequestDTO();
        // all null — should not change anything

        PeladaResponseDTO result = peladaService.updatePelada(10L, req, "admin@example.com");

        assertThat(result.getName()).isEqualTo("Sunday Pelada");
    }

    // --- deletePelada ---

    @Test
    void deletePelada_success_deletesPelada() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        peladaService.deletePelada(10L, "admin@example.com");

        verify(peladaRepository).delete(pelada);
    }

    @Test
    void deletePelada_cascadesDailiesMessagesRankingsAndRebuildsStats() {
        Daily d1 = Daily.builder().id(100L).pelada(pelada).dailyDate(java.time.LocalDate.now()).dailyTime("18:00").build();
        Daily d2 = Daily.builder().id(101L).pelada(pelada).dailyDate(java.time.LocalDate.now()).dailyTime("18:00").build();
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(dailyRepository.findByPelada(pelada)).thenReturn(List.of(d1, d2));
        when(dailyService.deleteDailyData(d1)).thenReturn(Set.of(admin));
        when(dailyService.deleteDailyData(d2)).thenReturn(Set.of(member));

        peladaService.deletePelada(10L, "admin@example.com");

        verify(dailyService).deleteDailyData(d1);
        verify(dailyService).deleteDailyData(d2);
        verify(messageRepository).deleteByPelada(pelada);
        verify(rankingRepository).deleteByPelada(pelada);
        verify(mensalistaRepository).deleteByPelada(pelada);
        verify(peladaRepository).delete(pelada);
        verify(aggregateRebuildService).rebuild(isNull(), eq(new java.util.LinkedHashSet<>(List.of(admin, member))));
    }

    @Test
    void deletePelada_callerNotCreator_throwsForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> peladaService.deletePelada(10L, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void deletePelada_peladaNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

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
        when(mensalistaRepository.countByPelada(pelada)).thenReturn(1L);

        PeladaMemberDTO result = peladaService.setMensalista(10L, 2L, true, "admin@example.com");

        verify(mensalistaRepository).save(argThat(m -> m.getPelada() == pelada && m.getUser() == member));
        assertThat(result.isMensalista()).isTrue();
        assertThat(result.getStars()).isEqualTo(PeladaPlayerStars.DEFAULT_STARS);
    }

    @Test
    void setMensalista_limitFull_throwsConflict() {
        pelada.setMaxMensalistas(2);
        stubSetMensalista(admin, member);
        when(mensalistaRepository.countByPelada(pelada)).thenReturn(2L);

        assertThatThrownBy(() -> peladaService.setMensalista(10L, 2L, true, "admin@example.com"))
                .hasMessage("Limite de mensalistas atingido (2/2)")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(mensalistaRepository, never()).save(any());
    }

    @Test
    void setMensalista_alreadyMensalista_isIdempotentEvenWithTheLimitFull() {
        pelada.setMaxMensalistas(1);
        stubSetMensalista(admin, member);
        when(mensalistaRepository.existsByPeladaAndUser(pelada, member)).thenReturn(true);

        PeladaMemberDTO result = peladaService.setMensalista(10L, 2L, true, "admin@example.com");

        assertThat(result.isMensalista()).isTrue();
        verify(mensalistaRepository, never()).save(any());
        verify(mensalistaRepository, never()).countByPelada(any());
    }

    @Test
    void setMensalista_unmark_deletesTheRow() {
        stubSetMensalista(admin, member);
        when(mensalistaRepository.existsByPeladaAndUser(pelada, member)).thenReturn(true);

        PeladaMemberDTO result = peladaService.setMensalista(10L, 2L, false, "admin@example.com");

        verify(mensalistaRepository).deleteByPeladaAndUser(pelada, member);
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
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> peladaService.setMensalista(10L, 3L, true, "admin@example.com"))
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(mensalistaRepository);
    }

    // --- time zone and mensalista limit ---

    @Test
    void createPelada_withoutTimeZone_usesSaoPaulo_invalidZone_throwsBadRequest() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.save(any(Pelada.class))).thenAnswer(inv -> inv.getArgument(0));
        CreatePeladaRequestDTO req = new CreatePeladaRequestDTO();
        req.setName("P");
        req.setDayOfWeek("FRIDAY");
        req.setTimeOfDay("20:00");
        req.setDuration(1f);

        peladaService.createPelada(req, "admin@example.com");
        verify(peladaRepository).save(argThat(p -> p.getTimeZone().equals("America/Sao_Paulo") && p.getMaxMensalistas() == 0));

        req.setTimeZone("-03:00");
        assertThatThrownBy(() -> peladaService.createPelada(req, "admin@example.com"))
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
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
    }
}
