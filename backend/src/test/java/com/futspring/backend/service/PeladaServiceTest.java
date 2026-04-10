package com.futspring.backend.service;

import com.futspring.backend.dto.CreatePeladaRequestDTO;
import com.futspring.backend.dto.PeladaDetailResponseDTO;
import com.futspring.backend.dto.PeladaResponseDTO;
import com.futspring.backend.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.dto.UserResponseDTO;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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

    @Mock
    PeladaRepository peladaRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    FileUploadService fileUploadService;

    @Mock
    UserAuthenticationHelper userAuthHelper;

    PeladaService peladaService;

    User admin;
    User member;
    User outsider;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        peladaService = new PeladaService(peladaRepository, userRepository, fileUploadService, userAuthHelper);

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
    void setAdmin_targetNotFound_throwsNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peladaService.setAdmin(10L, 999L, true, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- searchUsers ---

    @Test
    void searchUsers_returnsMatchingUsers() {
        when(userRepository.searchByUsernameOrEmail("ali")).thenReturn(List.of(admin));

        List<UserResponseDTO> result = peladaService.searchUsers("ali");

        assertThat(result).hasSize(1);
    }

    @Test
    void searchUsers_noMatches_returnsEmptyList() {
        when(userRepository.searchByUsernameOrEmail("zzz")).thenReturn(Collections.emptyList());

        List<UserResponseDTO> result = peladaService.searchUsers("zzz");

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
}
