package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

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

    public boolean isWritable(Pelada pelada) {
        return subscriptionRepository.findByPeladaId(pelada.getId())
                .map(subscription -> SubscriptionState.of(subscription, Instant.now(clock)).writable())
                // Every pelada has a row (V19 backfill, creation); a missing one is a bug, never a reason to lock
                .orElseGet(() -> {
                    log.warn("Pelada {} has no subscription row; treated as writable", pelada.getId());
                    return true;
                });
    }

    /** 402: its own status, so the client can offer to renew instead of a generic error. */
    public void requireWritable(Pelada pelada) {
        if (!isWritable(pelada)) {
            throw new AppException(HttpStatus.PAYMENT_REQUIRED, READ_ONLY_MESSAGE);
        }
    }
}
