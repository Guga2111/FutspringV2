package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.notification.dto.PushSubscriptionRequestDTO;
import com.futspring.backend.domain.notification.dto.RemovePushSubscriptionRequestDTO;
import com.futspring.backend.domain.notification.dto.VapidPublicKeyDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// Web Push subscriptions of the caller's devices; every route reads the caller from the token
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

    private final PushSubscriptionService subscriptionService;

    // 204 when push isn't configured on this server
    @GetMapping("/vapid-public-key")
    public ResponseEntity<VapidPublicKeyDTO> vapidPublicKey() {
        return subscriptionService.vapidPublicKey().map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequestDTO request,
                                          @AuthenticationPrincipal String email) {
        subscriptionService.subscribe(request, email);
        return ResponseEntity.noContent().build();
    }

    // POST: a DELETE with a body is poorly supported (same reason as /players/remove)
    @PostMapping("/subscriptions/remove")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody RemovePushSubscriptionRequestDTO request,
                                            @AuthenticationPrincipal String email) {
        subscriptionService.unsubscribe(request.endpoint(), email);
        return ResponseEntity.noContent().build();
    }
}
