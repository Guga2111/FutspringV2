package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.subscription.dto.PeladaSubscriptionDTO;
import com.futspring.backend.domain.subscription.dto.PixClaimRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The creator's Assinatura area. */
@RestController
@RequestMapping("/api/v1/peladas/{peladaId}/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final PeladaSubscriptionService service;

    @GetMapping
    public ResponseEntity<PeladaSubscriptionDTO> get(@PathVariable Long peladaId,
                                                     @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(service.get(peladaId, email));
    }

    @PostMapping("/pix-claim")
    public ResponseEntity<PeladaSubscriptionDTO> claimPix(@PathVariable Long peladaId,
                                                          @Valid @RequestBody PixClaimRequestDTO request,
                                                          @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(service.claimPix(peladaId, request.plan(), email));
    }
}
