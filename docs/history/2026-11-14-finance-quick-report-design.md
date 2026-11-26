# Quick payment report from WhatsApp

Date: 2026-11-14
Status: implemented on `feat/finance-quick-report`; validation evidence below.

## Problem and approved direction

Players find the current payment-report flow slow: open the shared collection, locate the action, open a payment sheet, then submit. The approved direction is a shared quick-report link that opens the signed-in player's own obligation with a prominent action. Reporting creates a pending notice; only a financial manager's existing confirmation records receipt and changes cash.

Alternatives considered:

1. A short authenticated reporting screen, reached from the shared message (selected). Reuses existing authorization and report commands without a WhatsApp API integration.
2. A direct report button on the full collection. Fewer steps, but still requires navigating the collection interface.
3. A native WhatsApp interactive button. Requires an official messaging integration, sender-to-account identity binding and ongoing operation; outside this change.

## User experience

Use `/pelada/:id/financeiro/:listId/paguei` as an authenticated quick-report destination. Preserve the existing full-detail route and old shared links. Reuse the app's existing private layout and session restoration; show a compact content area rather than the finance tabs, participant list or tour.

The shared message retains its prices, Pix instructions and payment summary. Its final call to action becomes `✅ Já pagou? Avise aqui: {quickReportLink}`. One collection link works for everyone: the signed-in account determines the applicable charge, never a name, phone number, or user id supplied by the link.

The screen shows the collection name, signed-in player's name, current outstanding amount and a primary `Paguei — avisar responsável` button. Supporting text explains that this reports the displayed remaining amount and the responsible manager must still check it. An optional `Anexar comprovante` disclosure uses the existing JPEG/PNG limits and retention explanation. No extra confirmation dialog is needed. Provide a secondary `Ver cobrança completa` link so players can still access Pix and the existing details.

Opening the page only reads. Submission happens exclusively after an explicit button click, never on page load, login completion, or link preview. Success replaces the action with `Pagamento informado. Aguardando conferência.` No further navigation is required.

The existing authentication destination handling must restore this route after login. Session restoration may avoid login only in a browser context that already has a valid refresh cookie; a WhatsApp webview must not be assumed to share the installed PWA's session.

## States and financial invariants

- Loading and read failures have explicit states and retry actions; do not show actionable stale data from another collection or account.
- Only the caller's active charge is selected, including a financial manager reporting their own charge. Never select another player's charge as a fallback.
- An existing pending notice shows `Aguardando conferência`, without another submission action.
- A settled charge shows `Pagamento já confirmado`, without another submission action.
- An account with no active charge gets a clear non-actionable state. Guest charges are not matched to accounts by name.
- Closed/canceled/draft collections, canceled allocations and a read-only subscription do not allow a report. Server-side visibility and write restrictions remain authoritative.
- Unrelated accounts, removed members and mismatched parent/child ids retain the existing API denial behavior without exposing another member's payment details.
- A report captures the server-calculated outstanding amount. It does not accept an amount or identity from the URL, transfer funds, or enter cash.
- Disable duplicate clicks. Preserve the existing revision and UUID request-key behavior for uncertain retries. On conflict, refresh the state and require a fresh explicit action if reporting is still available; never automatically resubmit after a changed amount.
- A forwarded link grants no authority and contains no session or payment-report bearer token.

## Implementation boundaries

Reuse the existing authenticated finance reads and `POST /api/v1/peladas/{id}/finance/lists/{listId}/charges/{chargeId}/report` command declared by `client/src/api/finance.ts`. `FinanceLedgerService.report` already enforces own-account reporting, open lists, positive remaining balances and no pending notice, under the existing financial transaction boundary. The receipt remains optional.

Extend the current finance API/hook and routing ownership where appropriate. Reuse command revision, retry and response handling rather than creating a parallel submission mechanism. Keep the short screen limited to its required UI and avoid mounting the full collection interface underneath it. No new backend endpoint, migration, financial status or WhatsApp integration is planned. If existing contracts cannot support the screen correctly, document that gap before expanding the contract.

Use shadcn components, semantic tokens, Hugeicons and pt-BR copy. Verify light/dark themes at 375 px, keyboard access, accessible pending/error/success feedback and the primary action without unnecessary scrolling.

## Verification and documentation

Meaningful behavior coverage must prove:

1. The shared link opens the quick destination, while existing full-detail links keep working; login returns to the quick destination.
2. Loading or revisiting the URL performs no report request.
3. The explicit action submits only the signed-in player's charge and transitions to pending review without a second modal.
4. Existing pending/paid/no-charge and unwritable states cannot submit; managers can report their own obligation.
5. Duplicate clicks, failed/uncertain requests, conflict refresh and amount changes retain existing command protections.
6. Optional receipts retain existing validation and the no-receipt path remains one explicit action.

Update `client/docs/FRONTEND.md` for the route, share text, user flow and relevant hooks. Change `core/docs/BACKEND.md` only if backend behavior/contracts change. Check existing financial authorization regression coverage and run `bun run lint`, `bun run build` and `bun run test` for implementation. Run `./mvnw test` if backend code changes.

The implementation touches an authenticated financial workflow and must follow `docs/SECURITY_REVIEW.md`: Trail of Bits differential review of the final diff before opening/marking ready a security-relevant PR and again before merge. Record actual validation and review evidence; this design is not a completed security review.

## Out of scope

Native WhatsApp buttons or bots, bank/payment processing, automatic settlement, anonymous reporting, guest identity claiming, payment reminders and changes to the existing full financial workflow.

## Implementation evidence (2026-11-14)

- Added the lazy authenticated quick-report screen directly under the private shell; full collection links remain intact. Shared WhatsApp messages now use `/paguei`. No backend contract or migration change.
- Reused `useFinance` command ownership and `financeReportSchema`. Read generations gate financial actions during loading/conflict refresh; account and route ids scope the quick screen and retry state.
- Frontend lint passed (the existing TanStack Table warning remains), production build passed, 59 test files / 551 unit tests passed; 68 quick-report browser scenarios passed across 375px/desktop and light/dark, with the existing locally installed Chromium selected through a temporary test config.
- Existing backend financial regressions: `FinanceServiceTest,FinanceConcurrencyTest`, 50 passed with Java 25. No backend code changed.
- Production visual inspection at 1440, 768, 390 and 375px in both themes: 0 FAIL / 0 WARN; every final screenshot opened. Direction Pastel consumer, seed 1743302871, existing brand/type retained, no redraws or new imagery. Critique: design 7.5, usability 8.5, creativity 6.5, content 8.5; overall 7.7. Local evidence: `.taste-skill/finance-quick-report/`.
- Official Docker visual check of the existing WhatsApp share dialog: 4 passed (all viewport/theme projects); existing references remained within tolerance.
- Security impact: yes. Scoped Trail of Bits differential review evidence is kept under ignored `.security-reviews/`; review must be rechecked against the actual PR target and final head before opening/merge per `docs/SECURITY_REVIEW.md`.
