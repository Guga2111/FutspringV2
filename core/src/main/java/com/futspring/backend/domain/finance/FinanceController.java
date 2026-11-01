package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.finance.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/peladas/{peladaId}/finance")
@RequiredArgsConstructor
public class FinanceController {
    private final FinanceReadService reads;
    private final FinanceCollectionService collections;
    private final FinanceLedgerService ledger;
    private final FinanceAttachmentService attachments;

    @GetMapping public FinanceViews.Overview overview(@PathVariable Long peladaId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, @AuthenticationPrincipal String email) { return reads.overview(peladaId, email, page, size); }
    @GetMapping("/lists/{listId}") public FinanceViews.Detail detail(@PathVariable Long peladaId, @PathVariable Long listId,
            @AuthenticationPrincipal String email) { return reads.detail(peladaId, listId, email); }
    @GetMapping("/movements") public FinanceViews.Page<FinanceViews.Movement> movements(@PathVariable Long peladaId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal String email) { return reads.ledger(peladaId, email, page, size); }
    @GetMapping("/lists/{listId}/charges/{chargeId}/notices") public FinanceViews.Page<FinanceViews.Notice> noticeHistory(@PathVariable Long peladaId,
            @PathVariable Long listId, @PathVariable Long chargeId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal String email) { return reads.noticeHistory(peladaId, listId, chargeId, email, page, size); }
    @GetMapping("/audit") public FinanceViews.Page<FinanceViews.Audit> audit(@PathVariable Long peladaId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal String email) { return reads.audit(peladaId, email, page, size); }
    @GetMapping("/daily-candidates/{dailyId}") public List<FinanceViews.Candidate> candidates(@PathVariable Long peladaId,
            @PathVariable Long dailyId, @AuthenticationPrincipal String email) { return reads.importCandidates(peladaId, dailyId, email); }
    @PutMapping("/settings") public FinanceViews.Result settings(@PathVariable Long peladaId, @Valid @RequestBody FinanceRequests.Settings body,
            @AuthenticationPrincipal String email) { return collections.settings(peladaId, body, email); }
    @PutMapping("/managers") public FinanceViews.Result managers(@PathVariable Long peladaId, @Valid @RequestBody FinanceRequests.Managers body,
            @AuthenticationPrincipal String email) { return collections.managers(peladaId, body, email); }
    @PostMapping("/templates") public FinanceViews.Result createTemplate(@PathVariable Long peladaId, @Valid @RequestBody FinanceRequests.Template body,
            @AuthenticationPrincipal String email) { return collections.template(peladaId, null, body, email); }
    @PutMapping("/templates/{templateId}") public FinanceViews.Result updateTemplate(@PathVariable Long peladaId, @PathVariable Long templateId,
            @Valid @RequestBody FinanceRequests.Template body, @AuthenticationPrincipal String email) { return collections.template(peladaId, templateId, body, email); }
    @PostMapping("/lists") public FinanceViews.Result createList(@PathVariable Long peladaId, @Valid @RequestBody FinanceRequests.Collection body,
            @AuthenticationPrincipal String email) { return collections.collection(peladaId, null, body, email); }
    @PutMapping("/lists/{listId}") public FinanceViews.Result updateList(@PathVariable Long peladaId, @PathVariable Long listId,
            @Valid @RequestBody FinanceRequests.Collection body, @AuthenticationPrincipal String email) { return collections.collection(peladaId, listId, body, email); }
    @PostMapping("/lists/{listId}/duplicate") public FinanceViews.Result duplicate(@PathVariable Long peladaId, @PathVariable Long listId,
            @Valid @RequestBody FinanceRequests.Command body, @AuthenticationPrincipal String email) { return collections.duplicate(peladaId, listId, body, email); }
    @PostMapping("/lists/{listId}/publish") public FinanceViews.Result publish(@PathVariable Long peladaId, @PathVariable Long listId,
            @Valid @RequestBody FinanceRequests.Command body, @AuthenticationPrincipal String email) { return collections.transition(peladaId, listId, "PUBLISH", body, email); }
    @PostMapping("/lists/{listId}/close") public FinanceViews.Result close(@PathVariable Long peladaId, @PathVariable Long listId,
            @Valid @RequestBody FinanceRequests.Command body, @AuthenticationPrincipal String email) { return collections.transition(peladaId, listId, "CLOSE", body, email); }
    @PostMapping("/lists/{listId}/reopen") public FinanceViews.Result reopen(@PathVariable Long peladaId, @PathVariable Long listId,
            @Valid @RequestBody FinanceRequests.Command body, @AuthenticationPrincipal String email) { return collections.transition(peladaId, listId, "REOPEN", body, email); }
    @PostMapping("/lists/{listId}/cancel") public FinanceViews.Result cancel(@PathVariable Long peladaId, @PathVariable Long listId,
            @Valid @RequestBody FinanceRequests.Command body, @AuthenticationPrincipal String email) { return collections.transition(peladaId, listId, "CANCEL", body, email); }
    @PostMapping("/lists/{listId}/participants") public FinanceViews.Result participants(@PathVariable Long peladaId, @PathVariable Long listId,
            @Valid @RequestBody FinanceRequests.Participants body, @AuthenticationPrincipal String email) { return collections.participants(peladaId, listId, body, email); }
    @PostMapping("/lists/{listId}/charges") public FinanceViews.Result createCharge(@PathVariable Long peladaId, @PathVariable Long listId,
            @Valid @RequestBody FinanceRequests.Charge body, @AuthenticationPrincipal String email) { return collections.charge(peladaId, listId, null, false, body, email); }
    @PutMapping("/lists/{listId}/charges/{chargeId}") public FinanceViews.Result updateCharge(@PathVariable Long peladaId, @PathVariable Long listId,
            @PathVariable Long chargeId, @Valid @RequestBody FinanceRequests.Charge body, @AuthenticationPrincipal String email) { return collections.charge(peladaId, listId, chargeId, false, body, email); }
    @PostMapping("/lists/{listId}/charges/{chargeId}/replace") public FinanceViews.Result replaceCharge(@PathVariable Long peladaId, @PathVariable Long listId,
            @PathVariable Long chargeId, @Valid @RequestBody FinanceRequests.Charge body, @AuthenticationPrincipal String email) { return collections.charge(peladaId, listId, chargeId, true, body, email); }
    @PostMapping("/lists/{listId}/charges/{chargeId}/cancel") public FinanceViews.Result cancelCharge(@PathVariable Long peladaId, @PathVariable Long listId,
            @PathVariable Long chargeId, @Valid @RequestBody FinanceRequests.Command body, @AuthenticationPrincipal String email) { return collections.cancelCharge(peladaId, listId, chargeId, body, email); }
    @PostMapping("/lists/{listId}/charges/{chargeId}/receive") public FinanceViews.Result receive(@PathVariable Long peladaId, @PathVariable Long listId,
            @PathVariable Long chargeId, @Valid @RequestBody FinanceRequests.Receipt body, @AuthenticationPrincipal String email) { return ledger.receive(peladaId, listId, chargeId, body, email); }
    @PostMapping(value = "/lists/{listId}/charges/{chargeId}/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FinanceViews.Result report(@PathVariable Long peladaId, @PathVariable Long listId, @PathVariable Long chargeId,
            @Valid @RequestPart("command") FinanceRequests.Command command, @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal String email) { return ledger.report(peladaId, listId, chargeId, command, file, email); }
    @PostMapping("/lists/{listId}/charges/{chargeId}/notices/{noticeId}/confirm") public FinanceViews.Result confirm(@PathVariable Long peladaId,
            @PathVariable Long listId, @PathVariable Long chargeId, @PathVariable Long noticeId,
            @Valid @RequestBody FinanceRequests.Confirmation body, @AuthenticationPrincipal String email) { return ledger.confirm(peladaId, listId, chargeId, noticeId, body, email); }
    @PostMapping("/lists/{listId}/charges/{chargeId}/notices/{noticeId}/reject") public FinanceViews.Result reject(@PathVariable Long peladaId,
            @PathVariable Long listId, @PathVariable Long chargeId, @PathVariable Long noticeId,
            @Valid @RequestBody FinanceRequests.Command body, @AuthenticationPrincipal String email) { return ledger.reject(peladaId, listId, chargeId, noticeId, body, email); }
    @PostMapping("/lists/{listId}/charges/{chargeId}/refund") public FinanceViews.Result refund(@PathVariable Long peladaId, @PathVariable Long listId,
            @PathVariable Long chargeId, @Valid @RequestBody FinanceRequests.Refund body, @AuthenticationPrincipal String email) { return ledger.refund(peladaId, listId, chargeId, body, email); }
    @PostMapping("/movements") public FinanceViews.Result cash(@PathVariable Long peladaId, @Valid @RequestBody FinanceRequests.Cash body,
            @AuthenticationPrincipal String email) { return ledger.cash(peladaId, body, email); }
    @PostMapping("/movements/{movementId}/reverse") public FinanceViews.Result reverse(@PathVariable Long peladaId, @PathVariable Long movementId,
            @Valid @RequestBody FinanceRequests.Command body, @AuthenticationPrincipal String email) { return ledger.reverse(peladaId, movementId, body, email); }
    @PostMapping(value = "/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) public FinanceViews.Result defaultQr(@PathVariable Long peladaId,
            @Valid @RequestPart("command") FinanceRequests.Command command, @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal String email) { return attachments.qr(peladaId, null, command, file, email); }
    @PostMapping(value = "/lists/{listId}/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) public FinanceViews.Result listQr(@PathVariable Long peladaId,
            @PathVariable Long listId, @Valid @RequestPart("command") FinanceRequests.Command command, @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal String email) { return attachments.qr(peladaId, listId, command, file, email); }
    @GetMapping("/notices/{noticeId}/receipt") public ResponseEntity<byte[]> receipt(@PathVariable Long peladaId, @PathVariable Long noticeId,
            @AuthenticationPrincipal String email) { return image(attachments.receipt(peladaId, noticeId, email)); }
    @GetMapping("/qr/{qrId}") public ResponseEntity<byte[]> qr(@PathVariable Long peladaId, @PathVariable Long qrId,
            @AuthenticationPrincipal String email) { return image(attachments.qr(peladaId, qrId, email)); }
    private ResponseEntity<byte[]> image(FinanceAttachmentService.Image image) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline").header("X-Content-Type-Options", "nosniff").body(image.bytes());
    }
}
