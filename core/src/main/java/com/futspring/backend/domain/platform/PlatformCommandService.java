package com.futspring.backend.domain.platform;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.platform.dto.PlatformRequests;
import com.futspring.backend.domain.platform.dto.PlatformViews;
import com.futspring.backend.domain.subscription.PeladaSubscription;
import com.futspring.backend.domain.subscription.PeladaSubscriptionRepository;
import com.futspring.backend.domain.subscription.SubscriptionPayment;
import com.futspring.backend.domain.subscription.SubscriptionPaymentRepository;
import com.futspring.backend.domain.subscription.SubscriptionService;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The platform admin's commands on a pelada's subscription. Each one locks the pelada row first (the same lock as
 * the pelada's other writes), then changes the subscription through SubscriptionService, which records the event.
 */
@Service
@RequiredArgsConstructor
public class PlatformCommandService {

    private final PlatformAccess access;
    private final PlatformReadService readService;
    private final PeladaRepository peladaRepository;
    private final PeladaSubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;
    private final Clock clock;

    @Transactional
    public PlatformViews.PeladaDetail recordPayment(String email, Long peladaId, PlatformRequests.RecordPayment request) {
        User admin = access.requirePlatformAdmin(email);
        BigDecimal amount = new BigDecimal(request.amount());
        if (amount.signum() <= 0) {
            throw AppException.field("amount", "O valor deve ser maior que zero");
        }
        if (request.paidOn().isAfter(LocalDate.ofInstant(Instant.now(clock), SubscriptionService.BILLING_ZONE))) {
            throw AppException.field("paidOn", "A data do pagamento não pode ser no futuro");
        }
        Pelada pelada = lockPelada(peladaId);
        subscriptionService.recordPayment(subscriptionOf(pelada), admin, request.plan(), amount, request.paidOn(),
                text(request.note()));
        return readService.detailOf(pelada);
    }

    @Transactional
    public PlatformViews.PeladaDetail changeSubscription(String email, Long peladaId,
                                                         PlatformRequests.ChangeSubscription request) {
        User admin = access.requirePlatformAdmin(email);
        String note = text(request.note());
        if (note == null && request.action() != PlatformRequests.SubscriptionAction.SET_FOUNDER) {
            throw AppException.field("note", "Escreva uma nota");
        }
        switch (request.action()) {
            case SET_VALID_UNTIL -> require(request.validUntil(), "validUntil", "Informe a nova validade");
            case EXTEND_DAYS -> require(request.days(), "days", "Informe quantos dias");
            case SET_FOUNDER -> require(request.founder(), "founder", "Informe se é fundadora");
            case EXEMPT, CANCEL -> { }
        }
        Pelada pelada = lockPelada(peladaId);
        PeladaSubscription subscription = subscriptionOf(pelada);
        switch (request.action()) {
            case SET_VALID_UNTIL -> subscriptionService.setValidUntil(subscription, admin, request.validUntil(), note);
            case EXTEND_DAYS -> subscriptionService.extendDays(subscription, admin, request.days(), note);
            case EXEMPT -> subscriptionService.exempt(subscription, admin, note);
            case CANCEL -> subscriptionService.cancel(subscription, admin, note);
            case SET_FOUNDER -> subscriptionService.setFounder(subscription, admin, request.founder(), note);
        }
        return readService.detailOf(pelada);
    }

    @Transactional
    public PlatformViews.Payment voidPayment(String email, Long paymentId, PlatformRequests.VoidPayment request) {
        User admin = access.requirePlatformAdmin(email);
        SubscriptionPayment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pagamento não encontrado"));
        subscriptionService.voidPayment(payment, admin, request.reason().trim());
        return PlatformReadService.paymentView(payment);
    }

    private Pelada lockPelada(Long peladaId) {
        return peladaRepository.findByIdForUpdate(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
    }

    // Every pelada has one (V19 backfill, creation)
    private PeladaSubscription subscriptionOf(Pelada pelada) {
        return subscriptionRepository.findByPeladaId(pelada.getId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Esta pelada não tem assinatura"));
    }

    private static void require(Object value, String field, String message) {
        if (value == null) throw AppException.field(field, message);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
