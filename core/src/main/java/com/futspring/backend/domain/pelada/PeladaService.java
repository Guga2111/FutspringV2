package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.chat.MessageRepository;
import com.futspring.backend.domain.daily.DailyService;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.pelada.dto.CreatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.dto.PeladaDetailResponseDTO;
import com.futspring.backend.domain.pelada.dto.PeladaResponseDTO;
import com.futspring.backend.domain.pelada.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.domain.stats.AggregateRebuildService;
import com.futspring.backend.domain.stats.repository.RankingRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashSet;
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

        return PeladaResponseDTO.forAdmin(peladaRepository.save(pelada));
    }

    @Transactional(readOnly = true)
    public List<PeladaResponseDTO> getMyPeladas(String currentUserEmail) {
        User user = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        return toSummaries(peladaRepository.findByMembersContaining(user), user);
    }

    /** Peladas that both the caller and {@code userId} belong to (profile page). */
    @Transactional(readOnly = true)
    public List<PeladaResponseDTO> getSharedPeladas(Long userId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        if (!userRepository.existsById(userId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Usuário não encontrado");
        }
        return toSummaries(peladaRepository.findSharedPeladas(caller.getId(), userId), caller);
    }

    // Member counts, admin flags and next sessions in grouped queries instead of one per pelada
    private List<PeladaResponseDTO> toSummaries(List<Pelada> peladas, User caller) {
        if (peladas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = peladas.stream().map(Pelada::getId).toList();
        Map<Long, Integer> memberCounts = peladaRepository.countMembersByIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
        Set<Long> administered = new HashSet<>(peladaRepository.findAdministeredIds(ids, caller.getId()));

        // Two sessions of a pelada on the same date: keep the oldest one
        Map<Long, Daily> nextDailies = dailyRepository.findNextDailies(ids, UPCOMING_STATUSES, LocalDate.now()).stream()
                .collect(Collectors.toMap(d -> d.getPelada().getId(), d -> d,
                        (a, b) -> a.getId() <= b.getId() ? a : b));
        List<Long> nextIds = nextDailies.values().stream().map(Daily::getId).toList();
        Map<Long, Integer> confirmedCounts = nextIds.isEmpty() ? Map.of()
                : dailyRepository.countConfirmedByIds(nextIds).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
        Set<Long> confirmedByCaller = nextIds.isEmpty() ? Set.of()
                : new HashSet<>(dailyRepository.findConfirmedDailyIds(nextIds, caller.getId()));

        return peladas.stream()
                .map(p -> {
                    Daily next = nextDailies.get(p.getId());
                    PeladaResponseDTO.NextDailyDTO nextDaily = next == null ? null : PeladaResponseDTO.NextDailyDTO.builder()
                            .id(next.getId())
                            .date(next.getDailyDate())
                            .time(next.getDailyTime())
                            .status(next.getStatus())
                            .confirmedCount(confirmedCounts.getOrDefault(next.getId(), 0))
                            .capacity(p.getNumberOfTeams() * p.getPlayersPerTeam())
                            .isConfirmed(confirmedByCaller.contains(next.getId()))
                            .build();
                    return PeladaResponseDTO.from(p, memberCounts.getOrDefault(p.getId(), 0),
                            administered.contains(p.getId()), nextDaily);
                })
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

        return PeladaResponseDTO.forAdmin(peladaRepository.save(pelada));
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
        return PeladaResponseDTO.forAdmin(peladaRepository.save(pelada));
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
