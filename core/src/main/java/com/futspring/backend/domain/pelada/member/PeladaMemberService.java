package com.futspring.backend.domain.pelada.member;

import com.futspring.backend.domain.daily.DailyService;
import com.futspring.backend.domain.finance.FinancePreservation;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.pelada.dto.PeladaMemberDTO;
import com.futspring.backend.domain.pelada.dto.PeladaResponseDTO;
import com.futspring.backend.domain.pelada.invitation.PeladaInvitationService;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A pelada's members and their roles: removing members (also on account deletion), stars, mensalistas, admins
 * and handing the pelada to another admin. Each member action works on a list of members, all or nothing; the
 * single routes call it with one id.
 */
@Service
@RequiredArgsConstructor
public class PeladaMemberService {

    private final FinancePreservation financePreservation;
    private final PeladaRepository peladaRepository;
    private final PeladaPlayerStarsRepository playerStarsRepository;
    private final PeladaMensalistaRepository mensalistaRepository;
    private final UserRepository userRepository;
    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final DailyService dailyService;
    private final ApplicationEventPublisher eventPublisher;
    private final PeladaInvitationService invitationService;

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

    /**
     * Account deletion, with the user's peladas already locked and no pelada created by them: the user leaves
     * every pelada as a removed member would (admin, stars, mensalista, attendance and teams of the sessions that
     * haven't started, invitations, finance delegation) but nobody is notified and no link block is recorded.
     * Bulk statements, so the reads don't grow with the number of peladas.
     */
    public void removeAccountFromPeladas(User user, String anonymousName) {
        financePreservation.forgetAccount(user, anonymousName);
        invitationService.forgetAccount(user);
        playerStarsRepository.deleteByUser(user);
        mensalistaRepository.deleteByUser(user);
        dailyService.removeAccountFromUpcomingSessions(user);
        peladaRepository.deleteAdminRowsOfUser(user.getId());
        peladaRepository.deleteMemberRowsOfUser(user.getId());
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
        PeladaResponseDTO response = PeladaResponseDTO.forAdmin(peladaRepository.save(pelada),
                financePreservation.isManager(peladaId, caller.getId()));
        eventPublisher.publishEvent(new PeladaTransferredEvent(pelada, caller, target));
        return response;
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
