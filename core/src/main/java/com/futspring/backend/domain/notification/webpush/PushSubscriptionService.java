package com.futspring.backend.domain.notification.webpush;

import com.futspring.backend.domain.notification.dto.PushSubscriptionRequestDTO;
import com.futspring.backend.domain.notification.dto.VapidPublicKeyDTO;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The caller's Web Push subscriptions (one per browser/device). The owner always comes from the token. An endpoint
 * is unique: a device where another account logs in moves to the caller. At most MAX_PER_USER per user; past it the
 * least recently alive one is dropped.
 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    static final int MAX_PER_USER = 10;

    private final UserAuthenticationHelper userAuthHelper;
    private final PushSubscriptionRepository subscriptionRepository;
    private final WebPushConfig webPushConfig;
    private final Clock clock;

    // Empty when push isn't configured (no VAPID key): the client shows "unconfigured"
    public Optional<VapidPublicKeyDTO> vapidPublicKey() {
        return webPushConfig.publicKey().map(VapidPublicKeyDTO::new);
    }

    // Upsert by endpoint, idempotent: the client resends its subscription every time the app opens
    @Transactional
    public void subscribe(PushSubscriptionRequestDTO request, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        PushEndpointPolicy.requireValid(request.endpoint(), request.keys().p256dh(), request.keys().auth());
        PushSubscription subscription = subscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(() -> new PushSubscription(caller, request.endpoint(), request.keys().p256dh(),
                        request.keys().auth(), Instant.now(clock)));
        subscription.setUser(caller);
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        subscriptionRepository.save(subscription);

        long excess = subscriptionRepository.countByUserId(caller.getId()) - MAX_PER_USER;
        if (excess > 0) {
            subscriptionRepository.findOldestByUserId(caller.getId(), subscription.getId(), Limit.of((int) excess))
                    .forEach(subscriptionRepository::delete);
        }
    }

    // Only the caller's own: someone else's or an unknown endpoint is a silent no-op (nothing is revealed)
    @Transactional
    public void unsubscribe(String endpoint, String currentUserEmail) {
        User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);
        subscriptionRepository.deleteByEndpointAndUserId(endpoint, caller.getId());
    }
}
