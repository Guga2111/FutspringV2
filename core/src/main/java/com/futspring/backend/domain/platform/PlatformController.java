package com.futspring.backend.domain.platform;

import com.futspring.backend.domain.platform.dto.PlatformRequests;
import com.futspring.backend.domain.platform.dto.PlatformViews;
import com.futspring.backend.domain.subscription.SubscriptionStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

// The platform admin's panel; every service method answers 404 to anyone else (PlatformAccess)
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformReadService readService;
    private final PlatformCommandService commandService;

    @GetMapping("/overview")
    public PlatformViews.Overview overview(@AuthenticationPrincipal String email) {
        return readService.overview(email);
    }

    @GetMapping("/peladas")
    public PlatformViews.Page<PlatformViews.PeladaItem> peladas(@RequestParam(required = false) SubscriptionStatus state,
                                                                @RequestParam(required = false) String q,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size,
                                                                @AuthenticationPrincipal String email) {
        return readService.peladas(email, state, q, page, size);
    }

    @GetMapping("/peladas/{id}")
    public PlatformViews.PeladaDetail pelada(@PathVariable Long id, @AuthenticationPrincipal String email) {
        return readService.pelada(email, id);
    }

    @PostMapping("/peladas/{id}/payments")
    public PlatformViews.PeladaDetail recordPayment(@PathVariable Long id,
                                                    @Valid @RequestBody PlatformRequests.RecordPayment request,
                                                    @AuthenticationPrincipal String email) {
        return commandService.recordPayment(email, id, request);
    }

    @PostMapping("/peladas/{id}/subscription")
    public PlatformViews.PeladaDetail changeSubscription(@PathVariable Long id,
                                                         @Valid @RequestBody PlatformRequests.ChangeSubscription request,
                                                         @AuthenticationPrincipal String email) {
        return commandService.changeSubscription(email, id, request);
    }

    @PostMapping("/payments/{id}/void")
    public PlatformViews.Payment voidPayment(@PathVariable Long id,
                                             @Valid @RequestBody PlatformRequests.VoidPayment request,
                                             @AuthenticationPrincipal String email) {
        return commandService.voidPayment(email, id, request);
    }

    @GetMapping("/payments")
    public PlatformViews.Payments payments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal String email) {
        return readService.payments(email, from, to, page, size);
    }

    @GetMapping("/users")
    public PlatformViews.Page<PlatformViews.UserItem> users(@RequestParam(required = false) String q,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size,
                                                            @AuthenticationPrincipal String email) {
        return readService.users(email, q, page, size);
    }
}
