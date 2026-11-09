package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.subscription.dto.SubscriptionSummaryDTO;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Whether a pelada's sporting and financial data can be written, from its subscription's effective state. Every
 * command that writes them calls {@link #requireWritable} right after its permission check.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionGuard {

    public static final String READ_ONLY_MESSAGE =
            "Esta pelada está em somente leitura. Renove a assinatura para voltar a usar.";

    private final PeladaSubscriptionRepository subscriptionRepository;
    private final Clock clock;

    /** Members a pelada can have while it doesn't pay (TRIAL or PENDING). */
    public static final int TRIAL_MEMBER_CAP = 30;

    public static final String TRIAL_CAP_MESSAGE =
            "Esta pelada está no período de teste, que vai até 30 membros. Peça ao criador para assinar.";

    public boolean isWritable(Pelada pelada) {
        return status(pelada).map(SubscriptionStatus::writable).orElse(true);
    }

    /** 402: its own status, so the client can offer to renew instead of a generic error. */
    public void requireWritable(Pelada pelada) {
        if (!isWritable(pelada)) {
            throw new AppException(HttpStatus.PAYMENT_REQUIRED, READ_ONLY_MESSAGE);
        }
    }

    /**
     * Before someone new joins (invitation or link), with the pelada row locked so the member count can't race: a
     * read-only pelada takes nobody (402), a TRIAL or PENDING one up to {@link #TRIAL_MEMBER_CAP} members (409).
     */
    public void requireRoomForMember(Pelada pelada) {
        SubscriptionStatus status = status(pelada).orElse(null);
        if (status == SubscriptionStatus.READ_ONLY) {
            throw new AppException(HttpStatus.PAYMENT_REQUIRED, READ_ONLY_MESSAGE);
        }
        if ((status == SubscriptionStatus.TRIAL || status == SubscriptionStatus.PENDING)
                && pelada.getMembers().size() >= TRIAL_MEMBER_CAP) {
            throw new AppException(HttpStatus.CONFLICT, TRIAL_CAP_MESSAGE);
        }
    }

    /** What the client reads (detail pages); null without a row, which the client reads as writable like here. */
    public SubscriptionSummaryDTO summary(Pelada pelada) {
        return subscriptionRepository.findByPeladaId(pelada.getId()).map(this::summaryOf).orElse(null);
    }

    /** {@link #summary} of many peladas in one query (pelada lists); peladas without a row are left out. */
    public Map<Long, SubscriptionSummaryDTO> summaries(Collection<Long> peladaIds) {
        if (peladaIds.isEmpty()) return Map.of();
        return subscriptionRepository.findByPeladaIdIn(peladaIds).stream()
                .collect(Collectors.toMap(s -> s.getPelada().getId(), this::summaryOf));
    }

    private SubscriptionSummaryDTO summaryOf(PeladaSubscription subscription) {
        return SubscriptionSummaryDTO.of(SubscriptionState.of(subscription, Instant.now(clock)), subscription.getValidUntil());
    }

    private Optional<SubscriptionStatus> status(Pelada pelada) {
        Optional<SubscriptionStatus> status = subscriptionRepository.findByPeladaId(pelada.getId())
                .map(subscription -> SubscriptionState.of(subscription, Instant.now(clock)));
        // Every pelada has a row (V19 backfill, creation); a missing one is a bug, never a reason to lock
        if (status.isEmpty()) log.warn("Pelada {} has no subscription row; treated as writable", pelada.getId());
        return status;
    }
}
