# Financeiro

> The pelada finance area: routes, state, permissions, selection, WhatsApp sharing and private images. Part of the [frontend guide](README.md).

## Rules

1. Money is an exact two-decimal **string**, never a float: `normalizeFinanceAmount` for input, `formatFinanceMoney` for display.
2. Amounts, totals, statuses and permissions come from the server; the client never computes them.
3. Every write carries the revision and a UUID request key; the key survives uncertain retries of the same submission; a 409 refreshes and asks for a new explicit click, never overwrites.
4. Writes are disabled while `locked` (pending, refreshing, after a read failure, read-only pelada).
5. The open collection lives in the URL (`/financeiro/:listId`).
6. Financial images load through authenticated object URLs (`useFinanceImageUrl`), revoked on close; never `useImageUrl`.
7. Forms use react-hook-form + zod; the WhatsApp message `Textarea` is the one documented exception (never submitted).

## Where it lives

- `/pelada/:id/financeiro` (and `/:listId`) render `components/finance/FinanceTab` through the lazy `PeladaFinancePage`, so the module downloads only when the page opens. It is keyed by pelada id, remounts and refetches on return, has the compact area header and hides the chat (so nothing covers the financial controls).
- `/pelada/:id/financeiro/:listId/paguei` (`FinanceQuickReportPage`) is the WhatsApp destination: it uses `AppLayout` directly, without the pelada layout, the finance tabs or the tour (see [Quick payment report](#quick-payment-report)).
- `api/finance.ts`, `types/finance.ts` and `schemas/finance.ts` mirror the backend contracts in [Pelada finance](../../core/docs/finance.md). No realtime subscription; nothing here changes attendance or mensalistas.

## State: `useFinance`

`useFinance(peladaId, selected, select, writable)` loads the overview (one `scope` and page of collections) and the selected collection's detail.

- **The URL is the open collection** (`useParams().listId`): opening a collection navigates to it; "← Cobranças" and the Cobranças tab go back to `…/financeiro`; switching to another tab with a collection open replaces the URL with `…/financeiro`; a command result for another collection (create, duplicate) navigates to it. Back / forward, reload and shared links open the same collection; one the caller can't see (403 / 404) shows the detail's error state.
- **Keyed reads:** the detail and its error are kept by collection id, so another collection shows the skeleton, never the previous one. Stale results are dropped; the page refreshes when the browser becomes visible again.
- **`ready`** needs a successful overview and selected-detail read for the current route and read generation. **`locked`** (`pending || !writable`, also while refreshing or after a read failure) disables every write control: Nova cobrança, cash entries, the collection menu except Compartilhar no WhatsApp, Selecionar, Receber / Conferir / Informar pagamento, corrections, reversals, settings, QR uploads and templates. Reading, filters, pages and the WhatsApp message stay available.
- **Command responses** (`{overview, detail}`) are merged: the result's settings, permissions and balance replace the current ones, the collections on screen stay, and the current scope and page are reloaded (never back to page 1; a page that no longer exists falls back to the last one).
- **Revision and request key:** every write carries the revision and a UUID request key. A logical submission keeps its key and revision across uncertain retries; a changed payload gets a new key. Editors capture the revision when they open and keep it while moving between the details, payment and correction sheets. A 409 discards the command, refreshes both reads and shows the server's conflict: the user clicks again after seeing the new amount.
- **Pages:** collections, cash and audit use numbered pages (`ListPagination`, 10 collections and 20 history rows per page, from the API's `total`); a charge's older notices load 10 at a time. Summaries carry no components; the detail carries every component and up to 10 recent notices per charge.

## Roles and tabs

| Tab | Who |
|---|---|
| Cobranças ("Minhas cobranças" for members) | everyone |
| Caixa | whoever sees the cash (`permissions.viewCash`) |
| Histórico | responsáveis |
| Configurações | responsáveis; admins who aren't responsáveis see only the Responsáveis card |

- **Only designated responsáveis get the manager view** (`permissions.manage`). An admin or creator who isn't one gets the member view (and the member tour) with an `Alert` "Você não é responsável financeiro" pointing to Configurações (`appointManagers && !manage`).
- Responsáveis are appointed by the creator, admins and responsáveis; any member can be one. At least one is required (`financeManagersSchema` `.min(1)`; the server also answers 409).
- Visibility: in individual mode a member receives only their own charge, so their collection has no queue chips or search; transparent members see published participants and cash. Other people's notices, receipts and the audit stay restricted in both modes. All of it is decided by the server.
- Collections are split by the API's `scope`: Em aberto / Encerradas, each with its own pages.

## Creating a collection

`FinanceCreateForm` + `useCreateFinanceCollection`: purpose and per-person amount → people → review.

- **One `POST /lists/create`** creates the people, the charges and the optional publication atomically; no earlier step persists anything. A draft may have no participants; publishing needs at least one.
- A saved template can fill the form; price components, due date, notes, session link and Pix overrides live in one collapsible "Vencimento e outras opções" section, opened by its trigger or by a validation error inside it.
- Simple price mode ignores the inactive component fields; detailed mode validates and sums them exactly.
- `FinancePeoplePicker` (also used for later additions) starts with `FinanceMemberChecklist` (shared with the Responsáveis card: `normalizeSearch` search, Todos / Mensalistas / Diaristas chips, counter, select / unselect visible), then "Usar participantes de uma sessão" (explicit review of the session's confirmations, never automatic) and any number of guests. Guest names never link to accounts.

## Collection detail

- Queues: **A receber** (default), Conferir, Pagos, Todos, plus an accent-insensitive search; pending reviews and overdue unpaid rows come first.
- A row offers Receber or Conferir and Detalhes. Receiving prefills the remaining amount, accepts a smaller partial, and defaults visibly to today / Pix. Details hold the price composition, notices, review decisions, adjustment, replacement, refund and cancellation.
- **One status per charge** from `financeChargeStatus` (`CANCELED | PAID | PARTIAL | UNPAID`), labelled and coloured by the `Record`s in `types/finance.ts`; list statuses by `financeListStatusLabel` / `financeListStatusStyle`. "Pagamento para conferir", "Vencida" and "Convidado" are separate indicators.
- Editing a published charge (components, abatements, due date) asks for a reason. "Trocar participante" leaves out everyone with an active charge in the list (the server allows one per person) and keeps the money on the old, canceled allocation.
- Cancelling is explicit: the whole collection from its menu, one person from their details. A closed list keeps its debt and needs a justified reopen before new payments or notices.
- A member who owes something on an open list sees `FinancePixCard` (QR Code on a white frame, amount still owed, key with Copiar, recipient) above their charge and at the top of "Informar pagamento"; reporting includes managers reporting their own charge. Managers manage the collection's own QR Code under "Informações da cobrança e Pix" (a reason after publication).
- Notice and payment dates are shown in the pelada's zone (`formatInZone`, the workspace's `timeZone`).

## Selection: receive and adjust

On a draft or open list, managers get "Selecionar" (`useSelection` with `canAdjustCharge` as `selectable`):

- **Selectable:** every charge that isn't canceled and has no payment to review, paid ones included; the others show a disabled checkbox with `adjustChargeHint`. The selection survives search, chips and refreshes. "Selecionar mensalistas" adds the selectable charges of members who are mensalistas today (never guests).
- Each action counts only what it changes (`planBulkFinanceActions`), as in Membros; `SelectionBar` shows the count and the amount to receive.
- **Receber** (open lists only) opens `FinanceReceiveAllDialog` with the receivable part (`canReceiveCharge`: not paid, something outstanding) and says how many were left out. **One `POST /lists/{listId}/receive-all` records everything or nothing.**
- **Ajustar valor** opens `FinanceBulkChargeForm` (`makeFinanceBulkAdjustSchema(published)`): the same components and adjustment for everyone, a live per-person and total preview (`bulkAdjustSummary`, no floating point), the reason after publication; due dates are untouched. **One `POST /lists/{listId}/charges/adjust` changes everyone or nobody** (e.g. "Ana já pagou R$ 26,50, acima do novo valor").
- Success leaves selection mode with a toast; an error stays in the dialog's root and keeps the selection.
- These actions use the brand, not the highlight: the highlight is for competition and achievements.

## Sharing on WhatsApp

Managers of an open list get "Compartilhar no WhatsApp". `FinanceWhatsAppDialog` builds the text **once, when it opens, from the detail on screen** (no request) with `financeWhatsAppMessage(detail, link, today)` (`utils/financeWhatsApp.ts`, `today` from `todayInZone`):

- Name and due date; the list's components and total, with "Alguns participantes têm valor diferente." when an active charge has its own components or an adjustment.
- Then, whatever the members' visibility in the app: who still owes (with partial / overdue marks), who is under review, who paid, sorted with `normalizeSearch`; "Todos pagaram!" when nobody owes and nothing is under review. Empty blocks are left out, canceled charges never count, and a charge with a pending notice is neither charged nor counted as paid.
- Then the Pix line (none without a key) and `✅ Já pagou? Avise aqui: {origin}/pelada/{id}/financeiro/{listId}/paguei`.
- The text is editable and never saved (reopening rebuilds it). "Abrir no WhatsApp" opens `wa.me/?text=` and is disabled past `WHATSAPP_URL_MAX` (2000, "use Copiar"); Copiar and the share button (where `navigator.share` exists) use the current text. All are disabled when it is blank.
- Plain `useState`, no react-hook-form: the one documented exception to [Forms](forms.md#forms-mandatory), since the text never reaches the API.

## Quick payment report

`FinanceQuickReportPage` reads the subscription and the existing overview / detail; no new contract.

- State is remounted per account + pelada + collection and picks only the caller's non-canceled charge by `userId` (managers included; never a guest by name).
- No report action on a load or reload error, a pending notice, a settled or missing charge, a list that isn't open, or a read-only pelada.
- Only the explicit "Paguei — avisar responsável" submit sends the existing report command: no confirmation modal, no automatic write. The optional receipt reuses `financeReportSchema` (JPEG / PNG, 5 MiB).
- Success says the payment is pending review; only a manager's confirmation affects cash. "Ver cobrança completa" opens the full detail; login returns here through `PrivateRoute` and `authReturnTo` (a WhatsApp web view may need its own login).
- Retries keep the same key and revision for the same submission signature, which includes the outstanding amount and the receipt's metadata.

## Cash and history

- **Caixa** shows the recorded balance and the statement: receipts, other income, expenses, opening balance, refunds and reversals. Notices and unpaid amounts never touch the balance. A `DateRangeFilter` filters the statement on the server by `effectiveDate`; the balance stays the unfiltered total. A reversal reads "Anula: {original}" ("Anula um lançamento anterior" when the original is on another page).
- **Histórico** (`FinanceHistory kind="audit"`, managers only, mounted while open): type chips mapped to backend operations (`financeAuditGroupOperations`) and a date range in the pelada's zone, resolved by the server; a new filter goes back to page 1 (`useFinanceHistory` keeps the page per filter). Entries are rendered by `utils/financeAudit.ts`: `auditTitle`, `auditSubject` and up to 4 "antes → depois" lines from `auditChanges` (`[]` for an unknown shape), with the raw JSON under "Ver dados técnicos".

## Tour

`FinanceTour` starts the role's tour (`FINANCE_MANAGER` / `FINANCE_MEMBER`, steps from `financeTourSteps(manage, viewCash)` pointing at `data-tour` attributes) the first time, only on the collections list with nothing open over it. Finishing, closing or skipping marks it seen on the server (`/tours`), so it never starts again on any device. The "?" button closes any sheet, goes back to the list and restarts it.

## Forms and images

- Every finance form is react-hook-form + zod + `Field`, with the shared controls in `FinanceFields`, Calendar / Popover for dates, decimal strings and `applyServerErrors`.
- Receipts and QR images: JPEG / PNG only, 5 MiB, matching the server (WebP is excluded on purpose).
- Protected images load as authenticated `apiClient` blobs into object URLs (`useFinanceImageUrl` for QR Codes, keyed by image), revoked on close or change; the receipt viewer also closes at the receipt's expiry (7 days for DAILY, 30 for MONTHLY / OTHER, enforced by the server).

## Tests

Vitest covers Brazilian input normalization, exact sums, queue filtering and order, atomic-creation payloads, decimal contracts, command payloads, multipart reports, authenticated images and currency formatting. `e2e/finance-quick-report.spec.ts` covers the quick report (state gates, explicit submit, retries, conflicts, receipt, sharing, login return) in the four viewport / theme projects.
