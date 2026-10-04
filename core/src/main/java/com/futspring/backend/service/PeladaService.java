package com.futspring.backend.service;

import com.futspring.backend.dto.CreatePeladaRequestDTO;
import com.futspring.backend.dto.PeladaDetailResponseDTO;
import com.futspring.backend.dto.PeladaResponseDTO;
import com.futspring.backend.dto.PublicUserDTO;
import com.futspring.backend.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.entity.Daily;
import com.futspring.backend.entity.DailyStatus;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.PeladaAccessHelper;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.DailyRepository;
import com.futspring.backend.repository.MessageRepository;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.RankingRepository;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PeladaService {

    static final int MIN_SEARCH_LENGTH = 3;
    static final int MAX_SEARCH_RESULTS = 10;
    private static final Set<DailyStatus> UPCOMING_STATUSES = Set.of(DailyStatus.SCHEDULED, DailyStatus.CONFIRMED);

    private final PeladaRepository peladaRepository;
    private final UserRepository userRepository;
    private final DailyRepository dailyRepository;
    private final RankingRepository rankingRepository;
    private final MessageRepository messageRepository;
    private final FileUploadService fileUploadService;
    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyService dailyService;
    private final AggregateRebuildService aggregateRebuildService;

    @Transactional
    public PeladaResponseDTO createPelada(CreatePeladaRequestDTO request, String currentUserEmail) {
        User user = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Pelada pelada = Pelada.builder()
                .name(request.getName().trim())
                .dayOfWeek(request.getDayOfWeek())
                .timeOfDay(request.getTimeOfDay())
                .duration(request.getDuration())
                .address(request.getAddress())
                .reference(request.getReference())
                .autoCreateDailyEnabled(request.isAutoCreateDailyEnabled())
                .numberOfTeams(request.getNumberOfTeams())
                .playersPerTeam(request.getPlayersPerTeam())
                .creator(user)
                .build();

        pelada.getMembers().add(user);
        pelada.getAdmins().add(user);

        return PeladaResponseDTO.from(peladaRepository.save(pelada), 1, null);
    }

    @Transactional(readOnly = true)
    public List<PeladaResponseDTO> getMyPeladas(String currentUserEmail) {
        User user = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        return toSummaries(peladaRepository.findByMembersContaining(user));
    }

    /** Peladas that both the caller and {@code userId} belong to (profile page). */
    @Transactional(readOnly = true)
    public List<PeladaResponseDTO> getSharedPeladas(Long userId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        if (!userRepository.existsById(userId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Usuário não encontrado");
        }
        return toSummaries(peladaRepository.findSharedPeladas(caller.getId(), userId));
    }

    // Member counts and next session dates in two grouped queries instead of one per pelada
    private List<PeladaResponseDTO> toSummaries(List<Pelada> peladas) {
        if (peladas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = peladas.stream().map(Pelada::getId).toList();
        Map<Long, Integer> memberCounts = peladaRepository.countMembersByIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
        Map<Long, LocalDate> nextDates = dailyRepository.findNextDailyDates(ids, UPCOMING_STATUSES, LocalDate.now()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (LocalDate) row[1]));

        return peladas.stream()
                .map(p -> PeladaResponseDTO.from(p, memberCounts.getOrDefault(p.getId(), 0), nextDates.get(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PeladaDetailResponseDTO getPeladaDetail(Long id, String currentUserEmail) {
        User user = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(id);
        accessHelper.requireMember(pelada, user);
        return PeladaDetailResponseDTO.from(pelada);
    }

    @Transactional
    public void addPlayer(Long peladaId, Long targetUserId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireAdmin(pelada, caller);

        User target = findUser(targetUserId);
        if (accessHelper.isMember(pelada, target)) {
            throw new AppException(HttpStatus.CONFLICT, "O jogador já é membro desta pelada");
        }

        pelada.getMembers().add(target);
        peladaRepository.save(pelada);
    }

    @Transactional
    public void removePlayer(Long peladaId, Long targetUserId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireAdmin(pelada, caller);

        User target = findMember(pelada, targetUserId);
        if (accessHelper.isCreator(pelada, target)) {
            throw new AppException(HttpStatus.FORBIDDEN, "O criador da pelada não pode ser removido");
        }

        pelada.getMembers().remove(target);
        pelada.getAdmins().remove(target);
        peladaRepository.save(pelada);
    }

    @Transactional
    public void setAdmin(Long peladaId, Long targetUserId, boolean isAdmin, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireAdmin(pelada, caller);

        // Only members can be admins
        User target = findMember(pelada, targetUserId);
        if (accessHelper.isCreator(pelada, target)) {
            throw new AppException(HttpStatus.FORBIDDEN, "O criador da pelada é sempre administrador");
        }

        if (isAdmin) {
            pelada.getAdmins().add(target);
        } else {
            pelada.getAdmins().remove(target);
        }
        peladaRepository.save(pelada);
    }

    // Matches username or email, but only returns public fields (no email)
    @Transactional(readOnly = true)
    public List<PublicUserDTO> searchUsers(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < MIN_SEARCH_LENGTH) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Digite pelo menos " + MIN_SEARCH_LENGTH + " caracteres para buscar");
        }
        return userRepository.searchByUsernameOrEmail(q, PageRequest.of(0, MAX_SEARCH_RESULTS)).stream()
                .map(PublicUserDTO::from)
                .toList();
    }

    @Transactional
    public PeladaResponseDTO updatePelada(Long peladaId, UpdatePeladaRequestDTO request, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireAdmin(pelada, caller);

        if (request.getName() != null) pelada.setName(request.getName().trim());
        if (request.getDayOfWeek() != null) pelada.setDayOfWeek(request.getDayOfWeek());
        if (request.getTimeOfDay() != null) pelada.setTimeOfDay(request.getTimeOfDay());
        if (request.getDuration() != null) pelada.setDuration(request.getDuration());
        if (request.getAddress() != null) pelada.setAddress(request.getAddress());
        if (request.getReference() != null) pelada.setReference(request.getReference());
        if (request.getAutoCreateDailyEnabled() != null) pelada.setAutoCreateDailyEnabled(request.getAutoCreateDailyEnabled());

        return PeladaResponseDTO.from(peladaRepository.save(pelada));
    }

    /**
     * Deletes the pelada and everything that belongs to it: dailies (with results, teams and attendance),
     * chat messages and rankings. Global Stats of the players who played here are rebuilt without it.
     */
    @Transactional
    public void deletePelada(Long peladaId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        if (!accessHelper.isCreator(pelada, caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Apenas o criador pode excluir a pelada");
        }

        Set<User> affected = new LinkedHashSet<>();
        for (Daily daily : dailyRepository.findByPelada(pelada)) {
            affected.addAll(dailyService.deleteDailyData(daily));
        }
        messageRepository.deleteByPelada(pelada);
        rankingRepository.deleteByPelada(pelada);

        pelada.getMembers().clear();
        pelada.getAdmins().clear();
        fileUploadService.deleteImageAfterCommit(pelada.getImage());
        peladaRepository.delete(pelada);

        // Global stats only: the pelada's own rankings are gone
        aggregateRebuildService.rebuild(null, affected);
    }

    @Transactional
    public PeladaResponseDTO uploadPeladaImage(Long peladaId, MultipartFile file, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireAdmin(pelada, caller);

        String filename = fileUploadService.uploadImage(file);
        fileUploadService.deleteImageAfterCommit(pelada.getImage());
        pelada.setImage(filename);
        return PeladaResponseDTO.from(peladaRepository.save(pelada));
    }

    private Pelada findPelada(Long id) {
        return peladaRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    private User findMember(Pelada pelada, Long userId) {
        User target = findUser(userId);
        if (!accessHelper.isMember(pelada, target)) {
            throw new AppException(HttpStatus.NOT_FOUND, "O jogador não é membro desta pelada");
        }
        return target;
    }
}
