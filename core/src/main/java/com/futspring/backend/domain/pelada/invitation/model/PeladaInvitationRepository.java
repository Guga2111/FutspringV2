package com.futspring.backend.domain.pelada.invitation.model;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;
import com.futspring.backend.domain.pelada.invitation.InvitationStatus;

public interface PeladaInvitationRepository extends JpaRepository<PeladaInvitation, Long> {
    @EntityGraph(attributePaths = {"pelada", "recipient", "sender"})
    @Query("""
        select i from PeladaInvitation i where i.pelada.id = :peladaId and
          (:pending is null or
           (:pending = true and i.status = com.futspring.backend.domain.pelada.invitation.InvitationStatus.PENDING and i.expiresAt > :now) or
           (:pending = false and (i.status <> com.futspring.backend.domain.pelada.invitation.InvitationStatus.PENDING or i.expiresAt <= :now)))
        order by i.createdAt desc, i.id desc
        """)
    List<PeladaInvitation> findForPelada(Long peladaId, Boolean pending, Instant now, Pageable page);
    @EntityGraph(attributePaths = {"pelada", "recipient", "sender"})
    List<PeladaInvitation> findByRecipientIdAndStatusAndExpiresAtGreaterThanOrderByCreatedAtDescIdDesc(
            Long recipientId, InvitationStatus status, Instant now, Pageable page);
    Optional<PeladaInvitation> findByPeladaIdAndRecipientIdAndStatus(Long peladaId, Long recipientId, InvitationStatus status);
    boolean existsByPeladaIdAndRecipientIdAndStatusAndRespondedAtGreaterThan(
            Long peladaId, Long recipientId, InvitationStatus status, Instant since);
    @Query("select i.pelada.id from PeladaInvitation i where i.id = :id")
    Optional<Long> findPeladaIdById(Long id);
    List<PeladaInvitation> findByPeladaIdAndRecipientIdInAndStatus(Long peladaId, Collection<Long> recipients, InvitationStatus status);
    void deleteByPeladaId(Long peladaId);

    // Account deletion: the recipient's open invitations end, as member removal does (canceled; expired when past due)
    @Modifying
    @Query("""
        UPDATE PeladaInvitation i SET i.status = com.futspring.backend.domain.pelada.invitation.InvitationStatus.CANCELED, i.canceledAt = :now
        WHERE i.recipient.id = :userId AND i.status = com.futspring.backend.domain.pelada.invitation.InvitationStatus.PENDING
          AND i.expiresAt > :now
        """)
    void cancelPendingForRecipient(@Param("userId") Long userId, @Param("now") Instant now);
    @Modifying
    @Query("""
        UPDATE PeladaInvitation i SET i.status = com.futspring.backend.domain.pelada.invitation.InvitationStatus.EXPIRED
        WHERE i.recipient.id = :userId AND i.status = com.futspring.backend.domain.pelada.invitation.InvitationStatus.PENDING
        """)
    void expirePendingForRecipient(@Param("userId") Long userId);
}
