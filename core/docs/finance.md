# Pelada finance

> The finance domain: transactions, permissions, REST contracts and private storage. Part of the [backend guide](README.md).

## Rules

1. Other domains reach finance only through `FinancePreservation`.
2. Every write goes through `FinanceTransactions`: pelada lock first, then authorization, revision, idempotency and audit, in one transaction.
3. Money is `NUMERIC(14,2)` in the database and an exact two-decimal JSON string on the wire.
4. Only designated managers manage; being creator or admin gives no financial access.
5. Receipts are private files with an expiry the server enforces; never in public uploads, logs or audit.

## Pelada finance (`domain/finance`)

V1 is a local, transactional ledger: no bank integration, no payment processing, and no effect on attendance or mensalista status.

### Services and boundaries

| Owner | Owns |
|---|---|
| `FinanceCollectionService` | settings, delegated managers, templates, collection lists, individual obligations |
| `FinanceLedgerService` | notices, receipts, realized cash, reversals, refunds |
| `FinanceReadService` | filtered read models and server totals |
| `FinanceAttachmentService` / `FinanceFiles` | authenticated images |
| `FinanceReceiptCleanup` | receipt expiry and orphan recovery |
| `FinancePreservation` | the only boundary other domains call: membership, deletion and the pelada read model (`managedPeladaIds`, `isManager` → `PeladaResponseDTO.isFinanceManager`); other domains never write finance tables |

The services stay at the root of `domain/finance` (they share package-private helpers); `collection/`, `ledger/` and `account/` hold each aggregate's entities and repositories.

### Commands

- `FinanceAccount` holds the visibility (individual / transparent), the default Pix and a revision that only goes up.
- Every write runs in `FinanceTransactions`: lock the pelada row **first**, then check membership, financial authority and revision, then update obligations, cash and `FinanceAudit` in the same transaction.
- Every command carries `command: {revision, requestKey: UUID, reason: string | null}`. The same key with the same caller, path, operation and payload returns the current authorized state without running again; a different payload or a stale revision → 409.
- Effective dates default to today in the pelada's zone and can't be in the future; timestamps come from the server clock.

### Money and identity

- Amounts are `NUMERIC(14,2)` and travel as **JSON strings** with exactly two decimals; JSON numbers, extra precision, formulas and non-finite values are rejected, and every amount and sum must fit the column.
- Price components are non-negative; adjustments may be negative; the server computes due, received and outstanding.
- `FinancePerson` keeps an account's identity by `(pelada_id, user_id)` across removal and return; a guest always gets a new identity, even with an equal name.
- `FinanceTemplate`, `FinanceList` and `FinanceCharge` each own their ordered `PriceComponent`s.
- One active charge per person and list. Replacing a participant cancels the old unpaid obligation, keeps its receipts on that old allocation and creates a new unpaid charge: money never moves between people. Refunding a canceled allocation must also reduce its kept obligation.

### Lists

- States: DRAFT → OPEN (publish) → CLOSED; a justified reopen goes CLOSED → OPEN. Cancel needs no pending notice and no valid receipt (allowed from draft and closed too). Published and canceled history stays.
- Closing needs every pending notice resolved; unpaid debt remains.
- The classification can't change after publication (it fixes the receipt retention). Editing a published charge or the list's instructions needs a reason and is audited before / after.
- List defaults apply to people added later; existing charges keep their own components and due dates.
- Duplicate copies active participants, values and instructions into a new draft, without notices, payments or receipts.
- The starter monthly / daily / event templates show as unsaved defaults on a pristine read and become real templates on the first write.

### Notices, cash and corrections

- A notice captures the owner's remaining obligation and the submission instant. Only the owner reports; one pending notice blocks other receipts and charge edits until it is resolved, and a notice produces at most one receipt (also unique in the database).
- Managers receive the remaining amount (`amount: null`) or an explicit positive partial, confirm a notice's captured amount, or reject it with a reason. A pending notice never enters cash. Managers may review their own payment; authorship is recorded.
- Cash is the sum of signed movements (opening, receipt, other income, expense, refund, reversal); price components never create entries; a negative balance is allowed.
- A reversal links to the immutable original, offsets its cash and obligation effect, happens once and can't itself be reversed. A corrected opening balance is entered after reversing the previous one.
- A refund's cash and its optional obligation reduction are atomic; no correction may leave negative net receipts or an obligation below the net receipts.

### Finance permissions and filtering

- **Only designated managers** (responsáveis, `finance_managers`) manage lists, templates, Pix, visibility, cash, reviews and the full audit. Being creator or admin gives no financial access: an admin who isn't a responsável gets the member view (`manage=false`, `appointManagers=true`). Designation grants no sporting or admin power.
- **Appointing** (`FinanceAccess`: `manage` = designated, `appoint` = manage, creator or admin): any current member can be designated, admins included. The managers command answers with the caller's permissions after the change (`FinanceAccess.refresh`), so someone who steps down gets the member view at once.
- **At least one responsável:** `PUT /managers` with an empty list → 409 "A pelada precisa de pelo menos um responsável financeiro"; removing the last one from the pelada (single or bulk) → 409 "Fulano é o único responsável financeiro. Designe outro antes de removê-lo." (`FinancePreservation.removeMembers`, before any change). A new pelada's creator starts as responsável (`FinancePreservation.appointCreator`, called by `PeladaService.createPelada` and the dev seed); becoming admin or receiving the pelada designates nobody.
- **Visibility:** individual (the default) gives ordinary members only their published charges and decisions: no cash, no other people's debts, audit or notice details. Transparent gives them published lists, participants (guests included), totals, cash and balance. Receipt bytes and notice history stay owner-or-manager in both modes.
- Every child id is scoped to the route's pelada and, for charge actions, its list. Permission flags come from the backend.

### Effects of other domains

- Removing a member revokes their delegation and access and keeps their charges and identity; a returning account recovers its old financial identity.
- Deleting a session takes the pelada lock and calls `FinancePreservation.detachDaily` (historical labels stay); cancelling a session has no financial effect.
- Deleting a pelada calls `deleteDraftOnlyPelada` before the sporting data: any publication or cash movement, even canceled or reversed, blocks the deletion for good; settings / draft-only peladas stay deletable. V12 also guards against an older binary: a trigger blocks the pelada delete, session FKs are `ON DELETE SET NULL`, managers reference membership with `ON DELETE CASCADE`.

### Finance REST contracts

All paths below are relative to `/api/v1/peladas/{peladaId}/finance`; authenticated caller identity always comes from the session. Mutation responses are `FinanceViews.Result {overview, detail}`: the new revision, current permissions/settings/balance, first 50 visible list summaries, manager templates, and the affected list detail when applicable. The client merges these responses. GET `/` returns one numbered page of summaries for a `scope`: `ACTIVE` (DRAFT/OPEN, the default) or `ARCHIVED` (CLOSED/CANCELED), filtered by scope and visibility in SQL before paging, with `total` (the caller's visible lists in that scope); mutation results always carry the first `ACTIVE` page. Summary `components` is empty (full components are on list detail). Histories use `{items, hasMore, total}` (a count query per page); page sizes are capped at 50, stable newest-id ordering. List detail returns the 10 latest notices per visible charge; older notices are paginated separately. Templates/charge components are fetched with entity graphs; summary totals batch charges, receipts and notices across the page.

| Method | Path | Body / result |
|---|---|---|
| GET | `/`, `?scope=ACTIVE&page=0&size=50` | `Overview {revision, permissions:{manage,appointManagers,viewCash}, settings:{visibility,pixKey,pixRecipient,qrId,managerIds}, balance:string|null, templates, lists, hasMore, total}`; `scope` `ACTIVE` \| `ARCHIVED` (unknown → 400) |
| PUT | `/settings` | `{command,visibility:INDIVIDUAL|TRANSPARENT,pixKey,pixRecipient,qrId} (null removes the default QR)` |
| PUT | `/managers` | `{command,userIds}`; creator, admin or responsável; 1+ unique current members (empty → 409); `settings.managerIds` is returned to whoever can appoint |
| POST / PUT | `/templates`, `/templates/{templateId}` | `{command,name,classification:DAILY|MONTHLY|OTHER,description,components:[{name,amount}],pixKey,pixRecipient,qrId,archived}` |
| POST / PUT | `/lists`, `/lists/{listId}` | Same configurable fields, with `dueDate` and `dailyId`, without `archived`; nullable dates/links, default Pix copied at creation |
| POST | `/lists/create` | `{collection:{command,...collection fields},userIds:[id],guestNames:[string],publish:boolean}`; creates the list and people/charges, optionally publishes, in one transaction/revision/audit; at most 100 members and 100 guests; publication requires people |
| GET | `/lists/{listId}` | `{collection,charges}`; each charge includes identity snapshot, components, adjustment, due/received/outstanding, payment status, overdue, canceled, dueDate and restricted recent notices |
| POST | `/lists/{listId}/{publish|close|reopen|cancel|duplicate}` | `Command` directly |
| GET | `/daily-candidates/{dailyId}` | Manager-only confirmed attendees `{userId,name,mensalista}` for explicit review; no automatic charge creation |
| POST | `/lists/{listId}/participants` | `{command,userIds,guestNames}`; atomic additions with the list's defaults |
| POST / PUT | `/lists/{listId}/charges`, `/lists/{listId}/charges/{chargeId}` | `{command,userId?,personId?,guestName?,components,adjustment,dueDate}`; creation selects exactly one identity, editing retains it |
| POST | `/lists/{listId}/charges/{chargeId}/{replace|cancel}` | Replacement uses the charge body with a new identity; cancellation uses `Command` |
| POST | `/lists/{listId}/charges/{chargeId}/receive` | `{command,amount:string|null,effectiveDate,method:PIX|CASH|TRANSFER|OTHER}`; null amount captures remaining balance |
| POST | `/lists/{listId}/receive-all` | `{command,chargeIds:[id] (1–200, unique),effectiveDate,method}`; "Receber selecionados": each charge receives its remaining amount, in one transaction, revision and audit entry (operation `RECEIVE`, target `LIST:{id}`). All or nothing: a charge of another list (404), canceled, already settled or with a pending notice (409) changes nobody; the list must be OPEN; manager only |
| POST | `/lists/{listId}/charges/adjust` | `{command,chargeIds:[id] (1–200, unique, else 400),components:[{name,amount}] (1–20, nonnegative),adjustment}`; "Ajustar valor": every charge gets the same components and adjustment (each keeps its due date and identity), in one transaction, revision and audit entry (operation `CHARGE_SAVE`, target `LIST:{id}`). Same rules as editing one charge: list not canceled, reason required after publication (400), manager only. All or nothing: a charge of another list (404), canceled, with a pending notice or already paid above the new value (409 naming the person) changes nobody |
| POST | `/lists/{listId}/charges/{chargeId}/report` | Multipart JSON `command` part, optional `file`; no amount, identity or timestamp from player |
| GET | `/lists/{listId}/charges/{chargeId}/notices?page=0&size=20` | Owner/manager-only paged notice history |
| POST | `/lists/{listId}/charges/{chargeId}/notices/{noticeId}/{confirm|reject}` | Confirm: `{command,effectiveDate,method}`; reject: `Command` with reason |
| POST | `/lists/{listId}/charges/{chargeId}/refund` | `{command,amount,effectiveDate,method,reduceObligation}` with reason |
| GET / POST | `/movements` | Paged statement, `?page&size&from&to` (optional ISO dates, inclusive on `effectiveDate`; `from` after `to` → 400 "Período inválido"; `total` is the filtered count, the overview `balance` stays the unfiltered total; without them the whole statement); create `{command,kind:OPENING|INCOME|EXPENSE,amount,description,category,effectiveDate,method,listId?,dailyId?}`; expenses need a category |
| POST | `/movements/{movementId}/reverse` | `Command` with reason |
| GET | `/audit`, `?page&size&from&to&operations` | Manager-only paginated actor/time/operation/reason and before/after JSON (no image contents). Optional filters: `from`/`to` ISO dates, inclusive days in the pelada's `timeZone` (`createdAt ≥ from 00:00` and `< to + 1 day 00:00`; `from` after `to` → 400 "Período inválido"), `operations` repeated (`?operations=RECEIVE&operations=REPORT`; unknown value → 400; none = all); `total` is the filtered count; without filters the whole history |
| POST | `/qr`, `/lists/{listId}/qr` | Multipart JSON `command` and image `file`; list instructions snapshot QR identity |
| GET | `/qr/{qrId}`, `/notices/{noticeId}/receipt` | Authenticated, scoped image bytes with `Cache-Control: no-store`, no-cache and nosniff |

The guided-create command reuses the collection-saving and participant-allocation rules in `FinanceCollectionService`; it does not issue nested financial commands or introduce new tables. Membership, scoped daily/QR links, unique member selection, exact amounts, permissions, revision, idempotency and audit are validated under the same pelada lock. `LIST_SAVE` audits the complete resulting detail. Any failure rolls back the list, people, charges and revision together. Identical retries return the one created list. Existing `/lists` and participant/publication endpoints remain compatible.

### Private financial storage and retention

- **Images:** JPEG / PNG only, at most 5 MiB, decoded by the shared `ImageDecoder` (type match, 20-Mpx ceiling, decoded at 256 px only to prove it is an image, the same 2-at-a-time permits: 503 when busy). Unlike public uploads the original bytes are stored, as private hashed files; WebP is refused.
- **Storage:** `FinanceFiles` rejects paths inside public uploads. `FINANCE_RECEIPTS_DIR` (`/app/finance-receipts`) and `FINANCE_QR_DIR` (`/app/finance-qr`) are separate private persistent mounts, provisioned for uid 10001 by `deploy.sh`; local defaults are separate working-directory folders, tests use temporary ones. New files are removed on rollback; crash orphans older than one hour are swept.
- **QR images** have immutable identities (changing the default never rewrites a published instruction) and follow the instruction's visibility, without expiry; they may be backed up.
- **Receipts** stop being readable exactly at submission + 7 real days (DAILY) or + 30 (MONTHLY / OTHER), even while pending; every GET rechecks membership, ownership and expiry, and expiry never resolves a notice. The 15-minute cleanup deletes expired files in id-cursor batches, retries failed deletions on later runs and resumes after a restart, so one failed file can't starve later batches; the access limit doesn't depend on the physical deletion.
- **Exclude `/opt/futspring-finance-receipts` from every durable host / file backup**; the database backup carries metadata and audit, never image bytes. Receipt bytes never go to logs or audit.

### Rollout and rollback

V12 is additive and creates no historical debts; V1 has no destructive contraction. A rollback rolls back the application binary and keeps the finance schema, records, mounts and deletion guards: never drop financial tables as an application rollback. Tests: the Finance row of [testing.md](testing.md#where-each-area-is-tested) (`FinanceConcurrencyTest` runs real PostgreSQL races: receive-all against a single receipt, a bulk adjust against a receipt, duplicate notice confirmation, retried guided creation, audit and file rollback).
