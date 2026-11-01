package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.chat.MessageRepository;
import com.futspring.backend.domain.finance.FinancePreservation;
import com.futspring.backend.domain.daily.AttendanceWindow;
import com.futspring.backend.domain.daily.DailyService;
import com.futspring.backend.domain.daily.dto.AttendanceBumpDTO;
import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.daily.entity.DailyStatus;
import com.futspring.backend.domain.daily.repository.DailyAttendanceBumpRepository;
import com.futspring.backend.domain.daily.repository.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.repository.DailyRepository;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.notification.NotificationRepository;
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
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
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

    private final FinancePreservation financePreservation;
    private final PeladaRepository peladaRepository;
    private final PeladaPlayerStarsRepository playerStarsRepository;
    private final PeladaMensalistaRepository mensalistaRepository;
    private final UserRepository userRepository;
    private final DailyRepository dailyRepository;
    private final DailyAttendanceRepository attendanceRepository;
    private final DailyAttendanceBumpRepository bumpRepository;
    private final RankingRepository rankingRepository;
    private final MessageRepository messageRepository;
    private final FileUploadService fileUploadService;
    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyService dailyService;
    private final AggregateRebuildService aggregateRebuildService;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PeladaInvitationService invitationService;

    @Transactional
    public PeladaResponseDTO createPelada(CreatePeladaRequestDTO request, String currentUserEmail) {
        User user = userAuthHelper.getAuthenticatedUser(currentUserEmail);

        Pelada pelada = Pelada.builder()
                .name(request.name().trim())
                .dayOfWeek(request.dayOfWeek())
                .timeOfDay(request.timeOfDay())
                .duration(request.duration())
                .address(request.address())
                .reference(request.reference())
                .autoCreateDailyEnabled(request.autoCreateDailyEnabled())
                .numberOfTeams(request.numberOfTeams())
                .playersPerTeam(request.playersPerTeam())
                .timeZone(request.timeZone() == null ? Pelada.DEFAULT_TIME_ZONE : validTimeZone(request.timeZone()))
                .maxMensalistas(request.maxMensalistas())
                .creator(user)
                .build();
        requireMaxWithinCapacity(pelada.getMaxMensalistas(), pelada);

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
                : attendanceRepository.countConfirmedByIds(nextIds).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
        Set<Long> confirmedByCaller = nextIds.isEmpty() ? Set.of()
                : new HashSet<>(attendanceRepository.findConfirmedDailyIds(nextIds, caller.getId()));
        Set<Long> mensalistaIn = new HashSet<>(mensalistaRepository.findPeladaIdsByUserAndPeladaIn(caller.getId(), ids));
        // Newest first, so the first bump kept per session is the caller's latest
        Map<Long, AttendanceBumpDTO> unseenBumps = nextIds.isEmpty() ? Map.of()
                : bumpRepository.findUnseenByUserAndDailyIn(caller.getId(), nextIds).stream()
                        .collect(Collectors.toMap(b -> b.getDaily().getId(), AttendanceBumpDTO::from, (newest, older) -> newest));

        return peladas.stream()
                .map(p -> {
                    Daily next = nextDailies.get(p.getId());
                    PeladaResponseDTO.NextDailyDTO nextDaily = next == null ? null : PeladaResponseDTO.NextDailyDTO.builder()
                            .id(next.getId())
                            .date(next.getDailyDate())
                            .time(next.getDailyTime())
                            .status(next.getStatus())
                            .confirmedCount(confirmedCounts.getOrDefault(next.getId(), 0))
                            .capacity(p.capacity())
                            .isConfirmed(confirmedByCaller.contains(next.getId()))
                            .startsAt(AttendanceWindow.startsAt(next))
                            .confirmOpensAt(mensalistaIn.contains(p.getId()) ? null : AttendanceWindow.diaristaOpensAt(next))
                            .bump(unseenBumps.get(next.getId()))
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
        return PeladaDetailResponseDTO.from(pelada, playerStarsRepository.findStarsByPelada(pelada),
                mensalistaRepository.findUserIdsByPelada(pelada));
    }

    @Transactional
    public void removePlayer(Long peladaId, Long targetUserId, String currentUserEmail) {
        removePlayers(peladaId, List.of(targetUserId), currentUserEmail);
    }

    /**
     * Removes several members at once, all or nothing: one target that isn't a member (404) or the creator (403)
     * fails the whole batch before anything changes.
     */
    @Transactional
    public void removePlayers(Long peladaId, List<Long> targetUserIds, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = peladaRepository.findByIdForUpdate(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        accessHelper.requireAdmin(pelada, caller);

        List<User> targets = findMembers(pelada, targetUserIds);
        if (targets.stream().anyMatch(t -> accessHelper.isCreator(pelada, t))) {
            throw new AppException(HttpStatus.FORBIDDEN, "O criador da pelada não pode ser removido");
        }

        financePreservation.removeMembers(pelada, targets, caller);
        invitationService.recordRemoval(pelada, targets);
        targets.forEach(pelada.getMembers()::remove);
        targets.forEach(pelada.getAdmins()::remove);
        // A player who comes back starts again with the default stars, as a diarista
        playerStarsRepository.deleteByPeladaAndUserIn(pelada, targets);
        mensalistaRepository.deleteByPeladaAndUserIn(pelada, targets);
        // Frees their spots in the sessions that haven't started; past sessions keep their results
        dailyService.removeFromUpcomingSessions(pelada, targets);
        peladaRepository.save(pelada);
        eventPublisher.publishEvent(new MembersRemovedEvent(pelada, caller, targets));
    }

    @Transactional
    public PeladaMemberDTO setPlayerStars(Long peladaId, Long targetUserId, int stars, String currentUserEmail) {
        return setPlayersStars(peladaId, List.of(targetUserId), stars, currentUserEmail).get(0);
    }

    // Any admin sets any member's stars in this pelada, their own included; the same value for every target
    @Transactional
    public List<PeladaMemberDTO> setPlayersStars(Long peladaId, List<Long> targetUserIds, int stars, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireAdmin(pelada, caller);

        List<User> targets = findMembers(pelada, targetUserIds);
        Map<Long, PeladaPlayerStars> existing = playerStarsRepository.findByPeladaAndUserIn(pelada, targets).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));
        List<PeladaPlayerStars> ratings = targets.stream()
                .map(t -> existing.getOrDefault(t.getId(), PeladaPlayerStars.builder().pelada(pelada).user(t).build()))
                .toList();
        ratings.forEach(r -> r.setStars(stars));
        playerStarsRepository.saveAll(ratings);

        return toMemberDTOs(pelada, targets);
    }

    @Transactional
    public PeladaMemberDTO setMensalista(Long peladaId, Long targetUserId, boolean isMensalista, String currentUserEmail) {
        return setMensalistas(peladaId, List.of(targetUserId), isMensalista, currentUserEmail).get(0);
    }

    /**
     * Any admin marks or unmarks members as mensalistas (idempotent per member). The pelada row is locked before
     * counting, so two admins at once can't go past maxMensalistas; a batch that doesn't fit changes nobody (the
     * system never picks who gets the status).
     */
    @Transactional
    public List<PeladaMemberDTO> setMensalistas(Long peladaId, List<Long> targetUserIds, boolean isMensalista,
                                                String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = peladaRepository.findByIdForUpdate(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        accessHelper.requireAdmin(pelada, caller);

        List<User> targets = findMembers(pelada, targetUserIds);
        Set<Long> current = mensalistaRepository.findUserIdsByPelada(pelada);
        if (isMensalista) {
            List<User> toAdd = targets.stream().filter(t -> !current.contains(t.getId())).toList();
            int max = pelada.getMaxMensalistas();
            if (current.size() + toAdd.size() > max) {
                throw new AppException(HttpStatus.CONFLICT, toAdd.size() == 1
                        ? "Limite de mensalistas atingido (" + current.size() + "/" + max + ")"
                        : "Limite de mensalistas atingido: cabem " + Math.max(0, max - current.size())
                                + ", foram selecionados " + toAdd.size());
            }
            mensalistaRepository.saveAll(toAdd.stream()
                    .map(t -> PeladaMensalista.builder().pelada(pelada).user(t).build())
                    .toList());
            eventPublisher.publishEvent(new MensalistasChangedEvent(pelada, caller, toAdd, true));
        } else {
            List<User> toRemove = targets.stream().filter(t -> current.contains(t.getId())).toList();
            if (!toRemove.isEmpty()) {
                mensalistaRepository.deleteByPeladaAndUserIn(pelada, toRemove);
            }
            eventPublisher.publishEvent(new MensalistasChangedEvent(pelada, caller, toRemove, false));
        }

        return toMemberDTOs(pelada, targets);
    }

    @Transactional
    public void setAdmin(Long peladaId, Long targetUserId, boolean isAdmin, String currentUserEmail) {
        setAdmins(peladaId, List.of(targetUserId), isAdmin, currentUserEmail);
    }

    // Only members can be admins, and the creator always is one; idempotent per member
    @Transactional
    public List<PeladaMemberDTO> setAdmins(Long peladaId, List<Long> targetUserIds, boolean isAdmin, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = peladaRepository.findByIdForUpdate(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        accessHelper.requireAdmin(pelada, caller);

        List<User> targets = findMembers(pelada, targetUserIds);
        if (targets.stream().anyMatch(t -> accessHelper.isCreator(pelada, t))) {
            throw new AppException(HttpStatus.FORBIDDEN, "O criador da pelada é sempre administrador");
        }

        // Only members whose status changes are notified (the route is idempotent)
        Set<Long> adminsBefore = new HashSet<>(peladaRepository.findAdminIds(pelada.getId()));
        List<User> changed = targets.stream().filter(t -> adminsBefore.contains(t.getId()) != isAdmin).toList();
        if (isAdmin) {
            pelada.getAdmins().addAll(targets);
        } else {
            targets.forEach(pelada.getAdmins()::remove);
        }
        // Flushed so the admin ids read for the response see the change
        peladaRepository.saveAndFlush(pelada);
        eventPublisher.publishEvent(new AdminsChangedEvent(pelada, caller, changed, isAdmin));

        return toMemberDTOs(pelada, targets);
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

        if (request.name() != null) pelada.setName(request.name().trim());
        if (request.dayOfWeek() != null) pelada.setDayOfWeek(request.dayOfWeek());
        if (request.timeOfDay() != null) pelada.setTimeOfDay(request.timeOfDay());
        if (request.duration() != null) pelada.setDuration(request.duration());
        if (request.address() != null) pelada.setAddress(request.address());
        if (request.reference() != null) pelada.setReference(request.reference());
        if (request.autoCreateDailyEnabled() != null) pelada.setAutoCreateDailyEnabled(request.autoCreateDailyEnabled());
        if (request.timeZone() != null) pelada.setTimeZone(validTimeZone(request.timeZone()));
        boolean capacityChanged = request.numberOfTeams() != null || request.playersPerTeam() != null;
        if (capacityChanged || request.maxMensalistas() != null) {
            // Same lock as setMensalista: the count can't change while the limit is checked
            peladaRepository.findByIdForUpdate(peladaId);
            if (request.numberOfTeams() != null) pelada.setNumberOfTeams(request.numberOfTeams());
            if (request.playersPerTeam() != null) pelada.setPlayersPerTeam(request.playersPerTeam());
            // The limit (new or current) must fit the resulting capacity
            int max = request.maxMensalistas() != null ? request.maxMensalistas() : pelada.getMaxMensalistas();
            requireMaxWithinCapacity(max, pelada);
            if (request.maxMensalistas() != null) {
                long current = mensalistaRepository.countByPelada(pelada);
                if (current > max) {
                    // The system never picks who loses the status
                    throw new AppException(HttpStatus.CONFLICT,
                            "Desmarque " + (current - max) + " mensalistas antes de reduzir o limite");
                }
                pelada.setMaxMensalistas(max);
            }
        }

        return PeladaResponseDTO.forAdmin(peladaRepository.save(pelada));
    }

    /**
     * Deletes the pelada and everything that belongs to it: dailies (with results, teams and attendance),
     * chat messages, notifications and rankings. Global Stats of the players who played here are rebuilt without it.
     */
    @Transactional
    public void deletePelada(Long peladaId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = peladaRepository.findByIdForUpdate(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        if (!accessHelper.isCreator(pelada, caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Apenas o criador pode excluir a pelada");
        }

        financePreservation.deleteDraftOnlyPelada(peladaId);
        Set<User> affected = new LinkedHashSet<>();
        for (Daily daily : dailyRepository.findByPelada(pelada)) {
            affected.addAll(dailyService.deleteDailyData(daily));
        }
        messageRepository.deleteByPelada(pelada);
        notificationRepository.deleteByPelada(pelada);
        invitationService.deletePeladaData(peladaId);
        rankingRepository.deleteByPelada(pelada);
        playerStarsRepository.deleteByPelada(pelada);
        mensalistaRepository.deleteByPelada(pelada);

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

    /** Back to the gradient with initials; the file is deleted after the commit. Idempotent. */
    @Transactional
    public PeladaResponseDTO removePeladaImage(Long peladaId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireAdmin(pelada, caller);

        fileUploadService.deleteImageAfterCommit(pelada.getImage());
        pelada.setImage(null);
        return PeladaResponseDTO.forAdmin(peladaRepository.save(pelada));
    }

    /**
     * The creator hands the pelada to another admin. The old creator stays member and admin; they only lose what
     * is reserved to the creator (delete, transfer, not being removable or demotable).
     */
    @Transactional
    public PeladaResponseDTO transferCreator(Long peladaId, Long userId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = peladaRepository.findByIdForUpdate(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        if (!accessHelper.isCreator(pelada, caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Apenas o criador pode transferir a pelada");
        }
        if (caller.getId().equals(userId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Você já é o criador da pelada");
        }
        User target = findMembers(pelada, List.of(userId)).get(0);
        if (!accessHelper.isAdmin(pelada, target)) {
            throw new AppException(HttpStatus.CONFLICT, "Só é possível transferir a pelada para um admin");
        }

        pelada.setCreator(target);
        PeladaResponseDTO response = PeladaResponseDTO.forAdmin(peladaRepository.save(pelada));
        eventPublisher.publishEvent(new PeladaTransferredEvent(pelada, caller, target));
        return response;
    }

    // IANA region ids only (America/Sao_Paulo), not offsets like "-03:00"
    private static String validTimeZone(String timeZone) {
        if (!ZoneId.getAvailableZoneIds().contains(timeZone)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Fuso horário inválido");
        }
        return timeZone;
    }

    private static void requireMaxWithinCapacity(int maxMensalistas, Pelada pelada) {
        if (maxMensalistas > pelada.capacity()) {
            throw new AppException(HttpStatus.CONFLICT,
                    "O limite de mensalistas não pode passar das vagas (" + pelada.capacity() + ")");
        }
    }

    private Pelada findPelada(Long id) {
        return peladaRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
    }

    /**
     * The members with these ids, in the same order, in two queries. Any id that isn't a member fails the whole
     * batch with 404, naming the player when the user exists.
     */
    private List<User> findMembers(Pelada pelada, List<Long> userIds) {
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Set<Long> memberIds = new HashSet<>(peladaRepository.findMemberIds(pelada.getId(), userIds));
        for (Long id : userIds) {
            if (!memberIds.contains(id)) {
                User user = users.get(id);
                throw new AppException(HttpStatus.NOT_FOUND, user == null
                        ? "O jogador não é membro desta pelada"
                        : user.getUsername() + " não é membro desta pelada");
            }
        }
        return userIds.stream().map(users::get).toList();
    }

    // The targets as the members grid shows them, in a fixed number of queries
    private List<PeladaMemberDTO> toMemberDTOs(Pelada pelada, List<User> targets) {
        Set<Long> adminIds = new HashSet<>(peladaRepository.findAdminIds(pelada.getId()));
        Map<Long, Integer> stars = playerStarsRepository.findStarsByPelada(pelada);
        Set<Long> mensalistaIds = mensalistaRepository.findUserIdsByPelada(pelada);
        return targets.stream()
                .map(t -> PeladaMemberDTO.from(t, adminIds.contains(t.getId()),
                        stars.getOrDefault(t.getId(), PeladaPlayerStars.DEFAULT_STARS),
                        mensalistaIds.contains(t.getId())))
                .toList();
    }
}
