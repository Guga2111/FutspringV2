package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.notification.dto.NotificationDTO;
import com.futspring.backend.domain.notification.dto.NotificationPreferenceDTO;
import com.futspring.backend.domain.notification.dto.UnsubscribeRequestDTO;
import com.futspring.backend.domain.notification.dto.UnreadCountDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Every route acts on the caller's notifications only; none takes a userId. Exception: unsubscribe and resubscribe
// are public and act on the user and category signed in the e-mail's token.
// @Validated enables validation of the List<@Valid NotificationPreferenceDTO> body
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationInboxService inboxService;
    private final NotificationPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<List<NotificationDTO>> list(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(defaultValue = "false") boolean unread,
                                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(inboxService.list(page, size, unread, email));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountDTO> unreadCount(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(inboxService.unreadCount(email));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id, @AuthenticationPrincipal String email) {
        inboxService.markRead(id, email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal String email) {
        inboxService.markAllRead(email);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public ResponseEntity<List<NotificationPreferenceDTO>> preferences(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(preferenceService.get(email));
    }

    @PutMapping("/preferences")
    public ResponseEntity<List<NotificationPreferenceDTO>> updatePreferences(
            @RequestBody @NotEmpty(message = "Informe pelo menos uma categoria")
            @Size(max = 4, message = "No máximo 4 categorias") List<@Valid NotificationPreferenceDTO> preferences,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(preferenceService.update(preferences, email));
    }

    // From the client's public page (JSON body)
    @PostMapping(value = "/unsubscribe", params = "!token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NotificationPreferenceDTO> unsubscribe(@Valid @RequestBody UnsubscribeRequestDTO request) {
        return ResponseEntity.ok(preferenceService.unsubscribe(request.token()));
    }

    // One-click from the mail provider (RFC 8058): POST to the List-Unsubscribe URL with the token in the query
    // and a "List-Unsubscribe=One-Click" form body, which is ignored
    @PostMapping(value = "/unsubscribe", params = "token")
    public ResponseEntity<NotificationPreferenceDTO> oneClickUnsubscribe(@RequestParam String token) {
        return ResponseEntity.ok(preferenceService.unsubscribe(token));
    }

    @PostMapping(value = "/resubscribe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NotificationPreferenceDTO> resubscribe(@Valid @RequestBody UnsubscribeRequestDTO request) {
        return ResponseEntity.ok(preferenceService.resubscribe(request.token()));
    }
}
