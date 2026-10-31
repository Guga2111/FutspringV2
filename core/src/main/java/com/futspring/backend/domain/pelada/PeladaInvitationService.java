package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.pelada.dto.*;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.HashSet;

import static com.futspring.backend.domain.pelada.InvitationStatus.*;

/** Every mutation locks the pelada first, then reads invitations/links/membership. GETs never change state. */
@Service
@RequiredArgsConstructor
public class PeladaInvitationService {
    private static final Duration VALIDITY = Duration.ofDays(7);
    private final PeladaRepository peladas;
    private final PeladaInvitationRepository invitations;
    private final PeladaInviteLinkRepository links;
    private final PeladaLinkBlockRepository blocks;
    private final UserRepository users;
    private final UserAuthenticationHelper authentication;
    private final PeladaAccessHelper access;
    private final InviteLinkTokens tokens;
    private final InvitationRateLimiter limiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public InvitationDTO invite(Long peladaId, Long userId, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        Pelada pelada = lock(peladaId);
        access.requireAdmin(pelada, actor);
        User recipient = users.findById(userId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Jogador não encontrado"));
        if (access.isMember(pelada, recipient)) throw error(HttpStatus.CONFLICT, "O jogador já participa da pelada");
        Instant now = clock.instant();
        var previous = invitations.findByPeladaIdAndRecipientIdAndStatus(peladaId, userId, PENDING);
        if (previous.isPresent()) {
            PeladaInvitation pending = previous.get();
            if (pending.statusAt(now) == PENDING) return InvitationDTO.from(pending, now, false);
            pending.setStatus(EXPIRED);
            invitations.saveAndFlush(pending); // release the partial unique index before inserting
        }
        if (invitations.existsByPeladaIdAndRecipientIdAndStatusAndRespondedAtGreaterThan(
                peladaId, userId, DECLINED, now.minus(VALIDITY))) {
            throw error(HttpStatus.CONFLICT, "Aguarde 7 dias após a recusa para enviar outro convite");
        }
        limiter.consume(actor.getId(), peladaId);
        PeladaInvitation invitation = invitations.save(PeladaInvitation.builder().pelada(pelada).recipient(recipient)
                .sender(actor).createdAt(now).expiresAt(now.plus(VALIDITY)).status(PENDING).build());
        events.publishEvent(new InvitationCreatedEvent(invitation));
        return InvitationDTO.from(invitation, now, false);
    }

    @Transactional(readOnly = true)
    public List<InvitationDTO> list(Long peladaId, Boolean pending, int page, int size, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        Pelada pelada = findPelada(peladaId);
        access.requireAdmin(pelada, actor);
        Instant now = clock.instant();
        List<PeladaInvitation> rows = invitations.findForPelada(peladaId, pending, now, page(page, size));
        if (rows.isEmpty()) return List.of();
        var memberIds = new HashSet<>(peladas.findMemberIds(peladaId,
                rows.stream().map(i -> i.getRecipient().getId()).toList()));
        return rows.stream().map(i -> InvitationDTO.from(i, now, memberIds.contains(i.getRecipient().getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<InvitationDTO> mine(int page, int size, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        Instant now = clock.instant();
        return invitations.findByRecipientIdAndStatusAndExpiresAtGreaterThanOrderByCreatedAtDescIdDesc(
                actor.getId(), PENDING, now, page(page, size)).stream()
                .map(i -> InvitationDTO.from(i, now, false)).toList();
    }

    @Transactional(readOnly = true)
    public InvitationDTO get(Long id, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        PeladaInvitation invitation = own(id, actor);
        return InvitationDTO.from(invitation, clock.instant(), access.isMember(invitation.getPelada(), actor));
    }

    @Transactional
    public JoinPeladaDTO accept(Long id, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        Pelada pelada = lockInvitation(id);
        PeladaInvitation invitation = own(id, actor);
        if (invitation.getStatus() == ACCEPTED) return new JoinPeladaDTO(pelada.getId());
        Instant now = clock.instant();
        requirePending(invitation, now);
        invitation.setStatus(ACCEPTED);
        invitation.setRespondedAt(now);
        blocks.deleteByPeladaIdAndUserId(pelada.getId(), actor.getId());
        join(pelada, actor);
        return new JoinPeladaDTO(pelada.getId());
    }

    @Transactional
    public void decline(Long id, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        lockInvitation(id);
        PeladaInvitation invitation = own(id, actor);
        if (invitation.getStatus() == DECLINED) return;
        Instant now = clock.instant();
        requirePending(invitation, now);
        invitation.setStatus(DECLINED);
        invitation.setRespondedAt(now);
    }

    @Transactional
    public void cancel(Long peladaId, Long id, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        Pelada pelada = lock(peladaId);
        access.requireAdmin(pelada, actor);
        PeladaInvitation invitation = findInvitation(id);
        if (!invitation.getPelada().getId().equals(peladaId)) throw error(HttpStatus.NOT_FOUND, "Convite não encontrado");
        if (invitation.getStatus() == CANCELED) return;
        Instant now = clock.instant();
        requirePending(invitation, now);
        invitation.setStatus(CANCELED);
        invitation.setCanceledAt(now);
    }

    @Transactional(readOnly = true)
    public InviteLinkDTO activeLink(Long peladaId, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        access.requireAdmin(findPelada(peladaId), actor);
        return links.findByPeladaIdAndRevokedAtIsNull(peladaId).filter(l -> clock.instant().isBefore(l.getExpiresAt()))
                .map(this::linkDTO).orElse(null);
    }

    @Transactional
    public InviteLinkDTO replaceLink(Long peladaId, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        Pelada pelada = lock(peladaId);
        access.requireAdmin(pelada, actor);
        limiter.consume(actor.getId(), peladaId);
        Instant now = clock.instant();
        links.findByPeladaIdAndRevokedAtIsNull(peladaId).ifPresent(l -> l.setRevokedAt(now));
        links.flush(); // revoke before inserting: only one unrevoked row is allowed
        return linkDTO(links.save(PeladaInviteLink.builder().pelada(pelada).creator(actor).createdAt(now)
                .expiresAt(now.plus(VALIDITY)).build()));
    }

    @Transactional
    public void revokeLink(Long peladaId, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        Pelada pelada = lock(peladaId);
        access.requireAdmin(pelada, actor);
        links.findByPeladaIdAndRevokedAtIsNull(peladaId).ifPresent(l -> l.setRevokedAt(clock.instant()));
    }

    @Transactional(readOnly = true)
    public PeladaPreviewDTO preview(String token) {
        return PeladaPreviewDTO.from(validLink(linkId(token)).getPelada());
    }

    @Transactional
    public JoinPeladaDTO joinLink(String token, String email) {
        User actor = authentication.getAuthenticatedUser(email);
        Long id = linkId(token);
        // Scalar lookup only before the lock: no stale managed link can survive a concurrent revocation.
        Long peladaId = links.findPeladaIdById(id).orElseThrow(this::invalidLink);
        Pelada pelada = lock(peladaId);
        validLink(id);
        if (access.isMember(pelada, actor)) return new JoinPeladaDTO(peladaId);
        if (blocks.existsByPeladaIdAndUserId(peladaId, actor.getId())) {
            throw error(HttpStatus.FORBIDDEN, "Você foi removido desta pelada. Peça um convite individual a um admin.");
        }
        Instant now = clock.instant();
        invitations.findByPeladaIdAndRecipientIdAndStatus(peladaId, actor.getId(), PENDING)
                .filter(i -> i.statusAt(now) == PENDING).ifPresent(i -> {
                    i.setStatus(ACCEPTED);
                    i.setRespondedAt(now);
                });
        join(pelada, actor);
        return new JoinPeladaDTO(peladaId);
    }

    /** Called by member removal while the same pelada row is locked, in the caller's transaction. */
    public void recordRemoval(Pelada pelada, List<User> targets) {
        Instant now = clock.instant();
        for (User user : targets) {
            if (!blocks.existsByPeladaIdAndUserId(pelada.getId(), user.getId())) {
                blocks.save(PeladaLinkBlock.builder().pelada(pelada).user(user).createdAt(now).build());
            }
        }
        invitations.findByPeladaIdAndRecipientIdInAndStatus(pelada.getId(), targets.stream().map(User::getId).toList(), PENDING)
                .forEach(i -> {
                    i.setStatus(i.statusAt(now) == EXPIRED ? EXPIRED : CANCELED);
                    if (i.getStatus() == CANCELED) i.setCanceledAt(now);
                });
    }

    /** Notifications are deleted first, so the invitation reference cannot obstruct deletion on H2 either. */
    public void deletePeladaData(Long peladaId) {
        invitations.deleteByPeladaId(peladaId);
        links.deleteByPeladaId(peladaId);
        blocks.deleteByPeladaId(peladaId);
    }

    private void join(Pelada pelada, User actor) {
        if (access.isMember(pelada, actor)) return;
        pelada.getMembers().add(actor);
        peladas.save(pelada);
        events.publishEvent(new MemberJoinedEvent(pelada, actor));
    }

    private PeladaInvitation own(Long id, User actor) {
        PeladaInvitation i = findInvitation(id);
        if (!i.getRecipient().getId().equals(actor.getId())) throw error(HttpStatus.FORBIDDEN,
                "Este convite pertence a outra conta. Entre com a conta que recebeu o convite.");
        return i;
    }
    private PeladaInvitation findInvitation(Long id) {
        return invitations.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Convite não encontrado"));
    }
    private Pelada lockInvitation(Long id) {
        return lock(invitations.findPeladaIdById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Convite não encontrado")));
    }
    private Pelada findPelada(Long id) {
        return peladas.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
    }
    private Pelada lock(Long id) {
        return peladas.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
    }
    private void requirePending(PeladaInvitation i, Instant now) {
        if (i.statusAt(now) != PENDING) throw error(HttpStatus.CONFLICT,
                i.statusAt(now) == EXPIRED ? "Este convite expirou. Peça um novo convite." : "Este convite já foi encerrado.");
    }
    private Long linkId(String token) { return tokens.verify(token).orElseThrow(this::invalidLink); }
    private PeladaInviteLink validLink(Long id) {
        PeladaInviteLink l = links.findById(id).orElseThrow(this::invalidLink);
        if (l.getRevokedAt() != null || !clock.instant().isBefore(l.getExpiresAt())) throw invalidLink();
        return l;
    }
    private AppException invalidLink() { return error(HttpStatus.GONE, "Link inválido, revogado ou expirado. Peça um novo link."); }
    private InviteLinkDTO linkDTO(PeladaInviteLink l) {
        return new InviteLinkDTO(tokens.create(l.getId()), l.getCreatedAt(), l.getExpiresAt(), PublicUserDTO.from(l.getCreator()));
    }
    private static PageRequest page(int page, int size) { return PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 50)); }
    private static AppException error(HttpStatus status, String message) { return new AppException(status, message); }
}
