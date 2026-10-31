# Pelada finance — approved V1 design

## Purpose and ownership

Centralize configurable collection lists, payment review, expenses and the pelada's recorded cash balance, including players who never use the app. A `domain/finance` module shares the current PostgreSQL and deployment. Financial writes and integrations are owned by this module and participate in local transactions.

Included: templates, members and separately identified guests, partial payments, cash, temporary receipts, permissions and audit. Excluded: bank/payment integration, WhatsApp, credit wallets, automatic billing, budgets, future payables and financial realtime.

## Collections

Settings define individual (default) or transparent visibility, delegated financial managers, and default Pix instructions (key, recipient, optional bank-provided QR). Only creator/admins appoint managers; delegates must remain members and gain no sporting permissions. All managers can manage lists, templates, cash and full audit, and review their own payments with recorded authorship.

Templates initially cover monthly, daily and event collections; names and price components are configurable without programmable formulas. Every concrete list has explicit DAILY/MONTHLY/OTHER classification, purpose, optional due date and daily link. Monthly descriptions may mention covered sessions without automated consumption, proration or absence compensation. List instructions and charges are snapshots: changes to defaults, templates, presence or membership category never rewrite published collections.

Managers select members (with mensalista/diarista filters), review imported daily attendees, or create named guests. Guests and accounts are distinct identities; equal names never merge. There is one active charge per person per list, with no deduplication between lists. Payment never changes membership category or attendance rights.

States: DRAFT (manager only), OPEN (notices and receipts allowed), CLOSED (pending debts remain; a justified reopen is needed for further notices/receipts), CANCELED (no valid confirmed receipts or pending notices). Closing requires every notice resolved. Duplicating produces a draft with no payments, notices or attachments. Cancellation preserves cash expenses and other movements.

## Payments and corrections

Debt status (unpaid/partial/paid), overdue and pending review are independent server-calculated indicators. A player can submit one immutable pending notice for their own charge on an open list, optionally with a receipt. The server captures the exact outstanding amount, identity and time. Rejection requires a reason and permits a later new notice.

Managers can receive the remaining balance or an explicit partial amount without player action; receipt fields include effective date and payment method. Confirming a notice accepts exactly its captured amount with the effective receipt date. A pending notice must be resolved before another receipt or charge amount change. Incorrect amounts are rejected, then recorded separately. Undo uses an immutable reversal with a reason, preserving the original and recalculating cash and debt.

Published amount, due date, participant and Pix changes require reasons and before/after audit. Replacement cancels the old charge and creates a new identity allocation without transferring receipts. Discounts reduce obligation without creating cash and cannot reduce debt below net received. Refunds represent real cash outflow; the manager chooses whether the obligation stays or is reduced. Cash and any obligation adjustment are atomic. Reversal corrects an erroneous record; refund records actual money leaving.

Cash = opening balance + signed inflows/outflows including reversals. Notices and receivables are excluded. Collection price components do not create additional cash entries. Expenses are realized spending with description, category, amount, effective date and optional list/daily. Negative cash is allowed. Surplus goes to other income or a refund, never automatic future credit. Published financial records are never deleted by routine editing.

## Privacy, files and preservation

Individual members receive only their own published charges, instructions and notice decisions; no other debts, cash statement or administrative audit. Transparent members can see published list participants (including guests), amounts, payment status, cash movements and balance. Receipts remain manager-or-owner only in both modes. Backend filtering is authoritative.

Receipt files are server-validated JPEG/PNG, at most 5 MiB, outside public images and durable backups. Access requires authentication, authorization, no-cache headers and a fresh expiry check on every request. Access ends exactly 7 days after upload for DAILY, 30 days for MONTHLY/OTHER, even during pending review; expiry does not resolve notices. Restart-safe cleanup retries physical deletion and logs failures. Bank QR images follow Pix instruction visibility without receipt expiry.

Removal revokes membership and delegated management while preserving history; the same returning user recovers their historical identity. Daily cancellation/deletion does not change debt or cash; deletion detaches technical daily links but retains historical descriptions. Permanent pelada deletion is blocked after any publication or any cash movement, even if canceled/reversed. Settings-only and draft-only peladas remain deletable.

## API, consistency and client

REST under `/api/v1/peladas/{peladaId}/finance`, with explicit publish/close/reopen/cancel/report/confirm/reject/receive/reverse/refund commands. All child IDs are scoped to this pelada. Exact two-decimal NUMERIC storage and decimal strings in JSON; excess precision is rejected. Effective dates are separate from server timestamps. Caller identity comes from the session.

Every financial write locks the existing pelada row; permissions, optimistic revision, invariants, ledger and audit commit together. Request keys prevent duplicate command execution and one notice can create at most one receipt. Reads return permissions, filtered data and calculated totals; long histories are paginated.

A lazy Financeiro tab uses shadcn, semantic tokens, react-hook-form/zod, pt-BR copy, complete loading/error states, light/dark themes and a 375px layout. Managers work with lists, participant actions, pending review, ledger and audit. Data refreshes on return and after commands.

## Migration map and rollout

Current readers/writers: PeladaService owns membership and deletion; DailyService owns daily deletion; neither stores finance. New finance tables have isolated ownership, with explicit integration operations for member removal, daily detachment and draft-only deletion. No historical debt/payment is inferred.

Forward: add V12 tables, constraints, indexes and deletion safety triggers; deploy backend and client together. Existing sporting data and APIs retain their shapes. Old binaries can continue normal sporting operations; database guards protect finance history from old deletion paths. New financial writes remain transactional and idempotent.

Rollback: roll back application binaries while retaining additive schema and financial data. Do not drop finance tables or remove preservation guards. Financial operation resumes after redeploying a compatible binary. No destructive contraction is part of V1.

## Acceptance

Cover configurable monthly/daily/event lists; offline players and guests; partial/direct receipts; pending notices excluded from cash; duplicate confirmation; discounts/reversals/refunds/reopen; snapshot preservation; permissions and cross-pelada isolation; individual/transparent filtering; receipt authorization and exact 7/30-day expiry; cleanup restart/retry; daily detachment and pelada deletion protection; transaction/file rollback; API money contracts; additive migration and real PostgreSQL concurrency. Run `./mvnw test` and `bun run lint && bun run build && bun run test`.
