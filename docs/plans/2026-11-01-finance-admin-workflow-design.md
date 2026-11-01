# Finance: practical daily administration

## Approved direction

The user rejected V1's dense interface and approved reorganizing finance around the organizer's daily job: create a collection, see who has paid, receive or review a payment. Preserve financial rules, authorization, audit, retention, existing data and history. This is an interaction redesign, not a reduction in financial safety.

## Task sequence

- Context and diagnosis: completed against the existing implementation and desktop/mobile renders.
- Clarification and alternatives: compact the current screen or reorganize around collections; the user approved the latter and emphasized practical, easy administration.
- Design: compact collections, participant-first detail, one primary row action, short payment form, separate cash and configuration, advanced details on demand.
- Record this design and commit it before implementation.
- Implement, verify service/contract invariants and real browser workflows, then document final behavior.

## People, data and decisions

- Organizers check groups of roughly 10–40 members, on phones at the field or on desktop before a session.
- Daily/monthly/event collections contain member and guest obligations; published values remain snapshots.
- The primary question is “Who still needs to pay or have a payment reviewed?”
- A collection row identifies its purpose, date/status, amount received and outstanding amount.
- The participant view shows identity, remaining value, payment status and one action: Receive or Review.
- Paid and canceled charges remain available through filters/details, without occupying the pending queue.
- Full price composition, due date, prior notices and corrections belong to a person's detail panel.
- Cash contains realized money and has its own balance and clear “Add expense”/“Add income” actions.
- Individual members enter “My charges”; server visibility and receipt restrictions remain authoritative.
- Existing templates, Pix configuration, financial delegates and audit remain accessible in configuration/history.

## Interaction and layout

Use the existing shadcn/semantic-token system, with the achromatic-ledger direction (seed 1226977936), existing font/palette preserved, no new imagery or dependencies. Quiet surfaces, aligned amounts, meaningful status cues and compact rows carry the data. The product-specific element is its real participant payment queue.

On Financeiro, replace the tall pelada photo banner with a compact group header while keeping access to the other pelada tabs. The feature has two working destinations, Collections and Cash, plus an explicit configuration/history action. Do not stack a global balance card, collection totals card and Pix card before the participants.

Collection detail starts with its name, concise received/outstanding summary and participant filters (Pending, Review, Paid, All), then rows. Search filters within the active collection. On phones, the first participants must be visible without scrolling past financial explanation panels. In a row, the person's name and pending amount are primary. One Receive/Review button opens a short form; details and corrections stay behind a clearly named Details control. The manager's own row follows the same organizer workflow; self-report remains available from details.

The receive form uses one editable amount, prefilled with the remaining balance. Any smaller positive amount is a partial receipt. Today and Pix are visible defaults; date/method alternatives are disclosed only when requested. No extra confirmation or mandatory proof is introduced. Review shows the exact reported amount, optional proof and explicit confirm/reject decisions.

Create a collection in one guided flow: purpose/type/name and per-person amount → select members/guests (or review daily import) → review the amounts and people → create and publish, or save a draft. Defaults and advanced price/Pix settings stay available without dominating the form. Publication is explicit. The backend must create the list, people/charges and publication atomically with one revision, idempotency key and audit record, so retry/failure cannot leave half a wizard persisted. Reuse the existing collection/participant rule owners; add an endpoint without breaking current endpoints or changing the schema.

Accept natural Brazilian monetary input (45, 45,50, 1.234,56), normalize exact decimal strings at the UI/API boundary without floating-point arithmetic, and retain the strict server decimal-string contract. All displayed values use pt-BR formatting.

## Boundaries and verification

Existing readers/writers and money/state invariants stay in the finance services. No migration, destructive change, data rewrite or automatic inference is needed. The new create operation must reject foreign/non-member identities, duplicates, invalid amounts and unauthorized callers before committing; rollback must include charges, list, audit and revision. Existing API callers remain compatible. Application rollback simply restores the previous UI/API binary while retaining all records.

Verify amount parsing, payment-queue filters and ordering, API payloads, atomic creation/idempotent retries/rollback, original service tests and PostgreSQL concurrency. Run the required backend and frontend checks. Compare the old and new production UI at desktop/tablet/375px in both themes, including empty/loading/error states, collection creation, payment review, partial receipt, correction access and the ordinary member view. Keep user-owned untracked files unchanged. Do not deploy or open a PR unless requested.

## Implementation and verification — 2026-11-01

The final workflow uses the existing design tokens and font, with the achromatic-ledger direction (seed 1226977936), no redraws, new assets or dependencies. Its signature is the participant payment queue. Collections answer who still owes or needs review; creation answers who is being charged and how much; review asks whether the reported money was received; cash shows money already recorded; configuration and audit expose defaults/authority and changes without occupying the daily workspace.

The new `POST /lists/create` is additive. It reuses the existing collection and participant rules, commits one revision/audit and preserves existing endpoints and schema. Tests cover complete publication, empty drafts, nested REST validation, member authorization, exact totals, idempotent concurrent creation, foreign-member rejection, empty-publication rejection and committed rollback after audit failure. There is no database migration or data rewrite.

Required checks passed: `./mvnw test` (825 tests, zero skipped, including real PostgreSQL 17 concurrency/migration tests); `bun run lint` (zero errors, the existing TanStack Table/React Compiler warning), `bun run build`, and `bun run test` (202 tests). The build retains its existing large-chunk advisory. Source design lint reports zero failures/warnings.

Production-build Chrome verification covered light/dark at 1440, 768 and 375 px: no page overflow or console errors. Executed workflows include localized partial receipt (12.50 received, 32.50 remaining), exact two-person creation/publication (91.00, one revision), pending report without cash changes, review/confirmation, expense, correction access, member-only visibility, member report with immutable captured amount, network-error retry, stale-form conflict without a second receipt, keyboard progression, blocked empty publication and successful empty draft. Preview data is synthetic, created only in a disposable local database; no sample financial data ships in the app.

### Visual review

Round 1 moved the first phone participant above the fold by compacting navigation and totals, folding group areas into a mobile Select, and moving secondary actions into the collection menu. Round 2 fixed tablet name wrapping by keeping the two-line participant layout until the usable content width supports the desktop columns; it also kept wizard navigation visible while people scroll. Round 3 checked the final production renders in both themes.

The final app-screen critique: (1) participant identities, debt and Receive/Review dominate the working area; (2) panel edges align and the tablet no longer compresses names into vertical fragments; (3) existing brand/palette are preserved with quiet finance surfaces; (4) displayed values reconcile with server balances and exact review totals; (5) counts and zero values remain explicit, including empty filters/drafts; (6) there are no chart legends or unrelated metrics; (7) mobile navigation folds, the first participant starts at 481 px in a 900 px viewport, and primary form navigation remains visible. All screenshot widths/themes were opened and reviewed.

The final rendered inspector reports zero failures and one contrast warning, inherited from the application's brand text and existing shell/tab tokens (about 3.8–4.3:1 in the light theme). No inspector checks were disabled. The sidebar section label's failing contrast was corrected with semantic tokens. A broader brand/theme contrast change remains outside this finance redesign. Existing financial safeguards, private-image retention and history remain covered by the backend suite.
