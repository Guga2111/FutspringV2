package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.pelada.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PeladaInvitationController {
    private final PeladaInvitationService service;

    @PostMapping("/peladas/{id}/invitations")
    public InvitationDTO invite(@PathVariable Long id, @Valid @RequestBody InvitePlayerRequestDTO request,
                                @AuthenticationPrincipal String email) {
        return service.invite(id, request.userId(), email);
    }
    @GetMapping("/peladas/{id}/invitations")
    public List<InvitationDTO> list(@PathVariable Long id, @RequestParam(required = false) Boolean pending, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal String email) {
        return service.list(id, pending, page, size, email);
    }
    @PostMapping("/peladas/{id}/invitations/{invitationId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id, @PathVariable Long invitationId,
                                       @AuthenticationPrincipal String email) {
        service.cancel(id, invitationId, email);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/peladas/{id}/invite-link")
    public ResponseEntity<InviteLinkDTO> link(@PathVariable Long id, @AuthenticationPrincipal String email) {
        InviteLinkDTO link = service.activeLink(id, email);
        return link == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(link);
    }
    @PostMapping("/peladas/{id}/invite-link")
    public InviteLinkDTO replace(@PathVariable Long id, @AuthenticationPrincipal String email) {
        return service.replaceLink(id, email);
    }
    @DeleteMapping("/peladas/{id}/invite-link")
    public ResponseEntity<Void> revoke(@PathVariable Long id, @AuthenticationPrincipal String email) {
        service.revokeLink(id, email);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/invitations")
    public List<InvitationDTO> mine(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal String email) {
        return service.mine(page, size, email);
    }
    @GetMapping("/invitations/{id}")
    public InvitationDTO get(@PathVariable Long id, @AuthenticationPrincipal String email) { return service.get(id, email); }
    @PostMapping("/invitations/{id}/accept")
    public JoinPeladaDTO accept(@PathVariable Long id, @AuthenticationPrincipal String email) { return service.accept(id, email); }
    @PostMapping("/invitations/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long id, @AuthenticationPrincipal String email) {
        service.decline(id, email);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/invite-links/{token}")
    public PeladaPreviewDTO preview(@PathVariable String token) { return service.preview(token); }
    @PostMapping("/invite-links/{token}/join")
    public JoinPeladaDTO join(@PathVariable String token, @AuthenticationPrincipal String email) { return service.joinLink(token, email); }
}
