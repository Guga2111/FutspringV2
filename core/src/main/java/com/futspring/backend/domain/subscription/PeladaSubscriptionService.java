package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.subscription.dto.PeladaSubscriptionDTO;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** The creator's side of the subscription: what to pay, the Pix to pay it with, and "Já fiz o Pix". */
@Service
@RequiredArgsConstructor
public class PeladaSubscriptionService {

    static final int PAYMENTS_SHOWN = 20;

    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;
    private final PeladaRepository peladaRepository;
    private final PeladaSubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;
    private final BillingPixConfig pix;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PeladaSubscriptionDTO get(Long peladaId, String email) {
        User caller = userAuthHelper.getAuthenticatedUser(email);
        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        requireCreator(pelada, caller);
        return viewOf(pelada, subscriptionOf(pelada));
    }

    @Transactional
    public PeladaSubscriptionDTO claimPix(Long peladaId, SubscriptionPlan plan, String email) {
        User caller = userAuthHelper.getAuthenticatedUser(email);
        // the lock of the platform admin's commands: a payment recorded meanwhile can't be followed by a stale claim
        Pelada pelada = peladaRepository.findByIdForUpdate(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
        requireCreator(pelada, caller);
        if (!pix.configured()) {
            throw new AppException(HttpStatus.CONFLICT, "Pagamento por Pix indisponível. Fale com o suporte.");
        }
        PeladaSubscription subscription = subscriptionOf(pelada);
        subscriptionService.claimPix(subscription, caller, plan);
        return viewOf(pelada, subscription);
    }

    private void requireCreator(Pelada pelada, User caller) {
        if (!accessHelper.isCreator(pelada, caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Apenas o criador cuida da assinatura da pelada");
        }
    }

    // Every pelada has one (V19 backfill, creation)
    private PeladaSubscription subscriptionOf(Pelada pelada) {
        return subscriptionRepository.findByPeladaId(pelada.getId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Esta pelada não tem assinatura"));
    }

    private PeladaSubscriptionDTO viewOf(Pelada pelada, PeladaSubscription subscription) {
        Instant now = Instant.now(clock);
        int memberCount = peladaRepository.countMembersByIds(List.of(pelada.getId())).stream()
                .findFirst().map(row -> ((Number) row[1]).intValue()).orElse(0);
        List<PeladaSubscriptionDTO.PlanOffer> plans = Arrays.stream(SubscriptionPlan.values())
                .map(plan -> {
                    BigDecimal amount = SubscriptionPlans.price(plan, memberCount);
                    return new PeladaSubscriptionDTO.PlanOffer(plan, SubscriptionPlans.money(amount),
                            pix.code(amount, pelada.getId()).orElse(null));
                })
                .toList();
        List<PeladaSubscriptionDTO.Payment> payments = paymentRepository
                .findByPeladaIdAndVoidedAtIsNullOrderByPaidOnDescIdDesc(pelada.getId(), PageRequest.of(0, PAYMENTS_SHOWN))
                .stream()
                .map(p -> new PeladaSubscriptionDTO.Payment(p.getPaidOn(), SubscriptionPlans.money(p.getAmount()),
                        p.getPlan(), p.getPeriodEnd()))
                .toList();
        return new PeladaSubscriptionDTO(SubscriptionState.of(subscription, now), subscription.getValidUntil(),
                subscription.getPlan(), subscription.isFounder(), subscription.getPixClaimedAt(),
                subscription.getClaimedPlan(), pix.recipient().orElse(null), plans, payments);
    }
}
