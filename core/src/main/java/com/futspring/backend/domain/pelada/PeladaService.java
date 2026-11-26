package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.chat.MessageRepository;
import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.DailyService;
import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.domain.finance.FinancePreservation;
import com.futspring.backend.domain.notification.NotificationRepository;
import com.futspring.backend.domain.pelada.dto.CreatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.dto.PeladaDetailResponseDTO;
import com.futspring.backend.domain.pelada.dto.PeladaResponseDTO;
import com.futspring.backend.domain.pelada.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.invitation.PeladaInvitationService;
import com.futspring.backend.domain.pelada.member.PeladaMensalistaRepository;
import com.futspring.backend.domain.pelada.member.PeladaPlayerStarsRepository;
import com.futspring.backend.domain.stats.AggregateRebuildService;
import com.futspring.backend.domain.stats.model.RankingRepository;
import com.futspring.backend.domain.subscription.SubscriptionGuard;
import com.futspring.backend.domain.subscription.SubscriptionService;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A pelada's own lifecycle: create, read, update its settings, delete it with everything it owns, and its photo.
 * Members and their roles live in {@link com.futspring.backend.domain.pelada.member.PeladaMemberService}.
 */
@Service
@RequiredArgsConstructor
public class PeladaService {

    private final FinancePreservation financePreservation;
    private final PeladaRepository peladaRepository;
    private final PeladaPlayerStarsRepository playerStarsRepository;
    private final PeladaMensalistaRepository mensalistaRepository;
    private final UserRepository userRepository;
    private final DailyRepository dailyRepository;
    private final RankingRepository rankingRepository;
    private final MessageRepository messageRepository;
    private final FileUploadService fileUploadService;
    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyService dailyService;
    private final AggregateRebuildService aggregateRebuildService;
    private final NotificationRepository notificationRepository;
    private final PeladaInvitationService invitationService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionGuard subscriptionGuard;
    private final PeladaSummaryAssembler summaryAssembler;

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

        Pelada saved = peladaRepository.save(pelada);
        financePreservation.appointCreator(saved);
        subscriptionService.start(saved, user);
        return PeladaResponseDTO.forAdmin(saved, true);
    }

    @Transactional(readOnly = true)
    public List<PeladaResponseDTO> getMyPeladas(String currentUserEmail) {
        User user = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        return summaryAssembler.toSummaries(peladaRepository.findByMembersContaining(user), user);
    }

    /** Peladas that both the caller and {@code userId} belong to (profile page). */
    @Transactional(readOnly = true)
    public List<PeladaResponseDTO> getSharedPeladas(Long userId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Usuário não encontrado");
        }
        return summaryAssembler.toSummaries(peladaRepository.findSharedPeladas(caller.getId(), userId), caller);
    }

    @Transactional(readOnly = true)
    public PeladaDetailResponseDTO getPeladaDetail(Long id, String currentUserEmail) {
        User user = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(id);
        accessHelper.requireMember(pelada, user);
        return PeladaDetailResponseDTO.from(pelada, playerStarsRepository.findStarsByPelada(pelada),
                mensalistaRepository.findUserIdsByPelada(pelada), subscriptionGuard.summary(pelada));
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

        return PeladaResponseDTO.forAdmin(peladaRepository.save(pelada), financePreservation.isManager(peladaId, caller.getId()));
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
        subscriptionService.deletePeladaData(peladaId);

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
        return PeladaResponseDTO.forAdmin(peladaRepository.save(pelada), financePreservation.isManager(peladaId, caller.getId()));
    }

    /** Back to the gradient with initials; the file is deleted after the commit. Idempotent. */
    @Transactional
    public PeladaResponseDTO removePeladaImage(Long peladaId, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        Pelada pelada = findPelada(peladaId);
        accessHelper.requireAdmin(pelada, caller);

        fileUploadService.deleteImageAfterCommit(pelada.getImage());
        pelada.setImage(null);
        return PeladaResponseDTO.forAdmin(peladaRepository.save(pelada), financePreservation.isManager(peladaId, caller.getId()));
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

}
