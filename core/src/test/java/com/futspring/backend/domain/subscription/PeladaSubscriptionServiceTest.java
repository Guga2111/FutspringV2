package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.subscription.dto.PeladaSubscriptionDTO;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The platform without a billing Pix (BILLING_PIX_KEY unset): the screen gets no code and nobody can claim
@ExtendWith(MockitoExtension.class)
class PeladaSubscriptionServiceTest {

    static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");

    @Mock UserAuthenticationHelper userAuthHelper;
    @Mock PeladaRepository peladaRepository;
    @Mock PeladaSubscriptionRepository subscriptionRepository;
    @Mock SubscriptionPaymentRepository paymentRepository;
    @Mock SubscriptionService subscriptionService;

    PeladaSubscriptionService service;
    User creator;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        creator = User.builder().id(1L).username("criador").email("c@test").build();
        pelada = Pelada.builder().id(7L).name("Pelada").creator(creator).build();
        service = new PeladaSubscriptionService(userAuthHelper, new PeladaAccessHelper(peladaRepository),
                peladaRepository, subscriptionRepository, paymentRepository, subscriptionService,
                new BillingPixConfig("", "", "", ""), Clock.fixed(NOW, ZoneOffset.UTC), event -> { });
        when(userAuthHelper.getAuthenticatedUser("c@test")).thenReturn(creator);
    }

    @Test
    void get_withoutPix_hasNoRecipientNorCodes_butKeepsThePrices() {
        when(peladaRepository.findById(7L)).thenReturn(Optional.of(pelada));
        when(subscriptionRepository.findByPeladaId(7L))
                .thenReturn(Optional.of(new PeladaSubscription(pelada, SubscriptionKind.PENDING, null, NOW)));
        when(peladaRepository.countMembersByIds(List.of(7L))).thenReturn(List.<Object[]>of(new Object[]{7L, 5}));
        when(paymentRepository.findByPeladaIdAndVoidedAtIsNullOrderByPaidOnDescIdDesc(anyLong(), any()))
                .thenReturn(List.of());

        var view = service.get(7L, "c@test");

        assertThat(view.pixRecipient()).isNull();
        assertThat(view.plans()).allSatisfy(plan -> assertThat(plan.pixCode()).isNull());
        assertThat(view.plans()).extracting(PeladaSubscriptionDTO.PlanOffer::amount).containsExactly("19.90", "199.00");
    }

    @Test
    void claim_withoutPix_is409() {
        when(peladaRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pelada));

        assertThatThrownBy(() -> service.claimPix(7L, SubscriptionPlan.MONTHLY, "c@test"))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(subscriptionService);
    }

}
