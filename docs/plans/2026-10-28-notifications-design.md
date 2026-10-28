# Notifications

Date: 2026-10-28 · Status: implemented (PR 1–4 in `feat/notifications`) · Branch: `feat/notifications` (see [Delivery](#delivery))

## Goal

Give every user a notification center (bell with unread count, list, read/unread) and e-mails for the events that need attention outside the app. Today the only notification is the "Você perdeu a vaga" bump (`DailyAttendanceBump` + its own e-mail listener); it joins the general pipeline. The UserMenu item "Notificações — Em breve" becomes real.

Decisions:

| Topic | Decision |
|-------|----------|
| Channels | In-app center + e-mail (Resend). No Web Push, no WhatsApp |
| Events | Session, attendance/spot, membership, awards. Chat mentions are out (phase 2) |
| Preferences | Per category × channel: in-app always on, e-mail toggled per category |
| Realtime | STOMP, one connection per logged-in user (`/user/queue/notifications`) |
| Bump | Becomes a notification; the highlighted alert on Home / session stays; reading either marks both |
| E-mail volume | Immediate, only for urgent categories by default (Attendance, Membership on; Session, Awards off). No digest |
| Storage | One row per recipient with typed references (no rendered text, no JSONB) |

## Data model

Migration `V10__create_notifications.sql`, non-destructive.

### `notifications`

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGSERIAL` | |
| `recipient_id` | FK `users`, `NOT NULL`, `ON DELETE CASCADE` | |
| `type` | `VARCHAR(40) NOT NULL` + `CHECK` | `NotificationType` (`EnumType.STRING`) |
| `pelada_id` | FK `peladas`, nullable, `ON DELETE CASCADE` | context and link |
| `daily_id` | FK `dailies`, nullable, `ON DELETE CASCADE` | context and link |
| `actor_id` | FK `users`, nullable, `ON DELETE SET NULL` | who caused it (mensalista of a bump, admin who removed you); null for system events |
| `award` | `VARCHAR(20)`, nullable, `CHECK` (`PUSKAS`, `WILTBALL`, `ARTILHEIRO`, `GARCOM`) | only on `AWARD_WON` |
| `created_at` | `TIMESTAMPTZ NOT NULL` | `Instant.now(clock)` |
| `read_at` | `TIMESTAMPTZ`, nullable | null = unread |

Indexes:
- `(recipient_id, created_at DESC)` — the list.
- Partial `(recipient_id) WHERE read_at IS NULL` — the counter.
- One per FK: `pelada_id`, `daily_id`, `actor_id`.
- Unique partial `(recipient_id, type, daily_id) WHERE type IN ('SESSION_REMINDER', 'DIARISTA_WINDOW_OPEN')` — a production backstop for the scheduled events. The real guard is the job's `INSERT … SELECT … WHERE NOT EXISTS (…)` (see General rules), because the tests run on H2 `create-drop`, which never gets this partial index: a test relying on `ON CONFLICT DO NOTHING` would pass even with broken logic. `FlywayMigrationTest` checks that the index exists.

### `notification_preferences`

| Column | Type |
|--------|------|
| `user_id` | FK `users`, `ON DELETE CASCADE` |
| `category` | `VARCHAR(20)` + `CHECK` (`SESSION`, `ATTENDANCE`, `MEMBERSHIP`, `AWARDS`) |
| `email_enabled` | `BOOLEAN NOT NULL` |
| PK | `(user_id, category)` |

A row exists only when the user changed the default. Without a row, `NotificationCategory.defaultEmail` applies: `ATTENDANCE` and `MEMBERSHIP` on, `SESSION` and `AWARDS` off. No backfill; changing a default later reaches everyone who never touched it.

### Rules

- The category is not a column: `NotificationType.category()` in Java and a matching `Record` in the client.
- `daily_attendance_bumps` stays as the session's history. The bump's notification is `(recipient, DIARISTA_BUMPED, daily_id)`.
- Deleting a pelada or a daily deletes its notifications (no broken links). A "removed from pelada" notification disappears if the pelada is later deleted; accepted.
- The deletes are **explicit in the services**, like every other child of a daily or pelada: `DailyService.deleteDailyData` calls `notificationRepository.deleteByDaily(daily)` and `PeladaService.deletePelada` calls `deleteByPelada(pelada)`. The `ON DELETE CASCADE` FKs in V10 are only a safety net (as in V9): the tests build the schema with Hibernate `create-drop` and Flyway off, so those cascades don't exist there and a delete would fail with an FK violation.
- Retention: the daily 04:00 job deletes read notifications older than 90 days and any notification older than 180 days.
- No `@OneToMany` on `User`/`Pelada`; `Notification` is LAZY with `EntityIdentity`, like every entity.

## Event catalog

Each type has a category (which drives the e-mail preference) and an **emailable** flag: some types stay in-app even when the category is on.

### Attendance (`ATTENDANCE`, e-mail on by default)

| Type | Trigger | Recipients | E-mail | Text (example) |
|------|---------|-----------|--------|----------------|
| `DIARISTA_BUMPED` | mensalista confirms into a full session | the bumped diarista | yes | "Você perdeu a vaga em Pelada do Fut (sáb, 31/10) para Leal" |
| `SESSION_CANCELED` | status → `CANCELED` | confirmed players | yes | "A sessão de sáb, 31/10 da Pelada do Fut foi cancelada" |
| `ATTENDANCE_REMOVED_BY_ADMIN` | admin removes your attendance | the player | yes | "Leal removeu sua presença na sessão de sáb, 31/10" |
| `ATTENDANCE_CONFIRMED_BY_ADMIN` | admin confirms you (single or "Confirmar todos") | the player | no | "Leal confirmou sua presença na sessão de sáb, 31/10" |
| `SESSION_REMINDER` (scheduled) | the diarista window opens (24 h before) | mensalistas who haven't confirmed | yes | "Sessão amanhã às 20:00 e você ainda não confirmou" |

`SESSION_CANCELED` is here, not in Session, because it affects people who had a spot and must reach them by e-mail.

### Session (`SESSION`, e-mail off by default)

| Type | Trigger | Recipients | E-mail | Text |
|------|---------|-----------|--------|------|
| `SESSION_CREATED` | admin or the scheduler creates a daily | all members | yes | "Nova sessão na Pelada do Fut: sáb, 31/10 às 20:00" |
| `DIARISTA_WINDOW_OPEN` (scheduled) | 24 h before, session with spots left | diaristas who haven't confirmed | yes | "As confirmações abriram: sáb, 31/10 às 20:00" |
| `TEAMS_SORTED` | sort teams | players on the teams | no | "Times sorteados para sáb, 31/10: você está no Time Azul" |
| `SESSION_FINISHED` | finalize | players on the teams | yes | "Sessão de sáb, 31/10 finalizada: veja o resultado" |

### Membership (`MEMBERSHIP`, e-mail on by default)

| Type | Recipients | E-mail | Text |
|------|-----------|--------|------|
| `ADDED_TO_PELADA` | the added member | yes | "Leal adicionou você à Pelada do Fut" |
| `REMOVED_FROM_PELADA` | the removed member | yes | "Você foi removido da Pelada do Fut" (no link) |
| `ADMIN_GRANTED` / `ADMIN_REVOKED` | the target | yes / no | "Você agora é admin da Pelada do Fut" |
| `MENSALISTA_GRANTED` / `MENSALISTA_REVOKED` | the target | yes / yes | "Você agora é mensalista da Pelada do Fut: confirme a qualquer momento" |
| `PELADA_TRANSFERRED` | the new creator | yes | "Leal transferiu a Pelada do Fut para você" |

### Awards (`AWARDS`, e-mail off by default)

| Type | Recipients | E-mail | Text |
|------|-----------|--------|------|
| `AWARD_WON` (+ `award`) | each winner of Puskás, Artilheiro, Garçom, Bola Murcha | yes | "Você foi o Artilheiro de sáb, 31/10 🏆" · "Você levou a Bola Murcha de sáb, 31/10" |

### General rules

1. The actor is never notified of their own action.
2. Bulk member actions create one notification per target with one `saveAll`. The ids are IDENTITY, so Hibernate disables JDBC batching and sends one INSERT per row; at pelada sizes (~30 members) that is accepted. The saved ids are collected for the after-commit push. Only `SESSION_CREATED` (every member) may move to a native `INSERT … SELECT FROM pelada_members`, and only if it is measured to be slow.
3. Repeatable events collapse: `TEAMS_SORTED` and `SESSION_FINISHED` reuse the **unread** notification with the same `(recipient, type, daily)` (its `created_at` is updated, so it goes back to the top) instead of creating another one. Covers re-sorting and re-finalizing.
4. Awards are notified on the first finalize only; editing the results of a finished session recomputes Artilheiro/Garçom without notifying again.
5. Stars never notify.
6. Scheduled events come from a job every 15 min that picks the sessions whose window opened since the last run, computed with `AttendanceWindow` (the pelada's time zone) and an injected `Clock` (tests use `Clock.fixed`). Duplicates are stopped by `INSERT … SELECT … WHERE NOT EXISTS (same recipient, type, daily)`; the unique partial index is the backstop in production. Each pelada is processed in its own try/catch, like `DailySchedulerService`.

### Pipeline

1. Services publish domain events (existing `DiaristaBumpedEvent`; new ones such as `DailyCreatedEvent`, `DailyCanceledEvent`, `TeamsSortedEvent`, `DailyFinalizedEvent`, `MembersAddedEvent`, `MembersRemovedEvent`, `AdminsChangedEvent`, `MensalistasChangedEvent`, `PeladaTransferredEvent`, `AttendanceChangedByAdminEvent`).
2. `NotificationService` listens **inside the caller's transaction** with a plain synchronous `@EventListener` (it runs at publish time, in the same transaction), resolves the recipients with grouped queries, drops the actor and saves the rows. A rollback leaves no notification. Not `@TransactionalEventListener(phase = BEFORE_COMMIT)`: that would have to publish a second event from inside the commit for an after-commit listener, which works but is subtle and hard to test.
3. The same listener registers the after-commit work with `TransactionSynchronizationManager.registerSynchronization` (`afterCommit`), the pattern `FileUploadService.deleteImageAfterCommit` already uses, passing the saved notification ids. After commit: STOMP push to every recipient on the default `@Async` pool, then the e-mail dispatch for the emailable types on the dedicated e-mail executor (see [Dispatch](#dispatch)).

## API and realtime

### REST (`NotificationController`, `/api/v1/notifications`)

Every route acts on the caller only (from the token); none takes a `userId`.

| Method | Path | Body | Response |
|--------|------|------|----------|
| GET | `/notifications?page=0&size=20&unread=false` | — | `List<NotificationDTO>`, newest first; `size` clamped to 1–50; `unread=true` only returns unread ones (the panel's "Não lidas" chip) |
| GET | `/notifications/unread-count` | — | `{count}` |
| POST | `/notifications/{id}/read` | — | 204; someone else's notification → 404; idempotent |
| POST | `/notifications/read-all` | — | 204 |
| GET | `/notifications/preferences` | — | `[{category, emailEnabled}]`, always the 4 categories with defaults applied |
| PUT | `/notifications/preferences` | `[{category, emailEnabled}]` (`@Valid`, unique categories, else 400) | same shape; stores only what differs from the default |
| POST | `/notifications/unsubscribe` | `{token}`, or `?token=` with the one-click form body (see Decisions PR 3) | **public**; `{category, emailEnabled: false}`; invalid token → 400 "Link inválido". Rate-limited in `AuthRateLimitFilter` |
| POST | `/notifications/resubscribe` | `{token}` | **public**; same token turns the category back on |

`NotificationDTO`:

```
{ id, type, category, award, createdAt, readAt,
  pelada: {id, name, image} | null,
  daily:  {id, date, time} | null,
  actor:  {id, username, image} | null,   // public fields, no e-mail
  team:   {name, color} | null }          // only on TEAMS_SORTED
```

- The list is a constant number of queries: fetch joins on pelada, daily and actor, plus one grouped query for the recipient's teams in the page's `TEAMS_SORTED` dailies.
- The client de-duplicates by `id` when "carregar mais" overlaps with a notification pushed to the top.

Bump integration: `POST /dailies/{id}/bumps/seen` also marks the `DIARISTA_BUMPED` notification of that daily as read, and reading that notification sets the bump's `seenAt`, in the same transaction.

### STOMP

- Server: after commit, `@Async`, `convertAndSendToUser(email, "/queue/notifications", dto)`. The STOMP session principal is already the e-mail (`JwtChannelInterceptor` sets it on CONNECT), so the user destination resolves without changes. A push failure is only logged; the row is already stored and the client catches up on reconnect.
- `JwtChannelInterceptor`: `SUBSCRIBE` also accepts `/user/queue/notifications` (today `/topic/pelada/{id}` and `/user/queue/errors`).
- Client: a `RealtimeProvider` in `AppLayout` opens **one** STOMP connection with backoff (the logic now in `usePeladaChat`) and exposes `subscribe(destination, handler)`. `usePeladaChat` uses it instead of opening its own connection.
- `useNotifications` (`src/hooks/`, with a context) holds `unreadCount` and the list: a pushed notification is prepended (or replaces the one with the same `id` when collapsed) and increments the counter when unread. On (re)connect and on `visibilitychange` it refetches `unread-count`, which covers anything missed while disconnected. No polling.
- The in-memory broker keeps the single-instance assumption (already documented).

## Preferences and e-mail

### When an e-mail is sent

All three: the type is emailable, the category is on for the user (row or default), and the `EmailSender` is real (`NoopEmailSender` only logs, as today). The preferences of all recipients of an event are read in one query.

### Dispatch

- `NotificationEmailDispatcher`, called from the after-commit synchronization (see [Pipeline](#pipeline)), reloads the new notifications with their joins and sends one e-mail per recipient.
- Pace: Resend accepts about 2 requests/s. Sends go through a **dedicated** `ThreadPoolTaskExecutor` bean (1 thread, bounded queue; not the default `@Async` pool, which has several threads and would break the pacing), with a Bucket4j bucket of 2/s inside it (30 e-mails ≈ 15 s, in background). A full queue logs and drops the e-mail; the in-app notification is already stored. Pushes stay on the default `@Async` pool. The batch endpoint isn't used because it doesn't accept attachments and the logo is an inline attachment (`cid:`).
- A failure for one recipient is logged and the rest continue; no retry (same policy as the password reset).
- Retires `DiaristaBumpedEmailListener`, `DiaristaBumpedEmail` and `email/diarista-bumped.html`.
- The password reset e-mail stays separate: transactional, not a notification, no unsubscribe.
- **Quota:** check the Resend plan before shipping (the free tier has a low daily/monthly cap). If it gets tight, the next step is the digest (phase 2).

### Template

- One generic `email/notification.html`, same structure and inline logo as `password-reset.html`. Placeholders (HTML-escaped): `{{title}}`, `{{body}}`, `{{ctaLabel}}`, `{{ctaUrl}}`, `{{categoryLabel}}`, `{{preferencesUrl}}`, `{{unsubscribeUrl}}`.
- Text from `NotificationEmailContent.of(notification)`, an exhaustive `switch` over `NotificationType`: a new type without e-mail text doesn't compile. Subject per type ("Você perdeu a vaga na Pelada do Fut", "Sessão cancelada: sáb, 31/10").
- The text exists in the client (bell) and the backend (e-mail); a test checks that every emailable type renders subject, title and CTA.

### One-click unsubscribe

- Footer: "Você recebe isso porque os e-mails de **Presença** estão ligados. [Desativar] · [Preferências]".
- Token: HMAC-SHA256 of `userId:category` with a key derived from `JWT_SECRET` (context `"notification-unsubscribe"`). No expiry, not stored, only turns that category of that user off or on.
- The link opens the public page `/notifications/unsubscribe?token=` in the client, which POSTs the token (a GET never changes anything, because mail scanners open links).
- Headers `List-Unsubscribe` and `List-Unsubscribe-Post: List-Unsubscribe=One-Click` pointing to the POST endpoint (deliverability with Gmail/Yahoo).

## UI

### Bell

- Desktop: `Button size="icon" variant="ghost"` with `Bell` in the `AppSidebar` header next to the logo; badge with the unread count (`bg-destructive`, "9+" past 9). With the sidebar collapsed (64 px) the icon stays and the badge becomes a dot.
- Mobile (< 768 px): the same button on the right of `MobileTopBar`.
- `aria-label` with the count: "Notificações, 3 não lidas".

### Panel

Same pattern as the chat: `Popover` on desktop (~380 px wide, max height with scroll), `Drawer` on mobile.

- Header: "Notificações", `Todas` / `Não lidas` chips (toggle `variant="chip"`), "Marcar todas como lidas", and a gear linking to the preferences.
- Item: the actor's `PlayerAvatar` (or `PeladaAvatar` for system events) with a small category icon, the text with names in bold, relative time ("há 5 min", `formatDistanceToNow` + `ptBR`), `bg-brand` dot and `bg-accent` background when unread.
- Clicking an item marks it read (optimistic), closes the panel and navigates to `/daily/:id` when there is a daily, else `/pelada/:id`; `REMOVED_FROM_PELADA` doesn't navigate.
- Footer: "Carregar mais" (pages of 20).
- States: item-shaped `Skeleton`; empty "Nenhuma notificação por aqui"; error `Alert` with "Tentar de novo".
- The list loads on first open; the counter loads at start.
- `utils/notifications.ts`: `Record<NotificationType, (n) => { text, href, icon }>` and `notificationCategoryLabel`, with Vitest tests.

### Toasts on push

Only for the Attendance category (bump, canceled, attendance removed, reminder): `toast` with a "Ver" action. Everything else only updates the counter.

### Preferences page — `/settings/notifications` (private)

- Opened from the UserMenu "Notificações" item (no longer "Em breve") and from the panel's gear.
- One `Card` "E-mail" with 4 rows, each with label, description and a `Switch` saved at once (optimistic, reverts with a toast on error, like the Automação card):
  - Presença e vaga — "Perdeu a vaga, sessão cancelada, lembrete de confirmação"
  - Sessões — "Nova sessão, confirmações abertas, sessão finalizada"
  - Membros — "Adicionado, removido, virou admin ou mensalista"
  - Prêmios — "Artilheiro, Garçom, Puskás, Bola Murcha"
- Note: "As notificações dentro do app ficam sempre ativas."

### Unsubscribe page — `/notifications/unsubscribe?token=` (public)

`AuthLayout`, like the reset screens. Posts on open, shows "E-mails de **Presença** desativados" with "Reativar" and "Gerenciar preferências" (goes through login when logged out). Invalid token → `Alert` "Link inválido".

### Integrations

- `dismissBump` (Home and session) also marks the bump's notification read in the notifications context and decrements the counter. The "Você perdeu a vaga" alert keeps its look.
- Semantic tokens only, light and dark, 375 px.

## Testing

Backend:
- `NotificationServiceTest`: recipients per type, actor excluded, collapse of repeatable events, awards only on the first finalize, no notification on rollback, after-commit work registered only when the transaction commits.
- Deleting a daily and a pelada that have notifications works on H2 (explicit deletes, no FK violation).
- `NotificationControllerTest`: list/count/read/read-all happy paths; reading or marking another user's notification → 404; `read-all` doesn't touch other users; preferences validation 400.
- E-mail: send matrix (type × preference × default), executor pace, template `switch` covers every type.
- Unsubscribe: tampered token or swapped category → 400; only that user and category change.
- `JwtChannelInterceptorTest`: `SUBSCRIBE /user/queue/notifications` without token rejected.
- Scheduled job: reminder and window-open recipients, idempotent across runs through `NOT EXISTS` (the H2 schema has no partial index, so this tests the real guard), pelada time zone with `Clock.fixed`.
- `FlywayMigrationTest` stays green with V10.

Frontend: `utils/notifications.test.ts` (text, href and icon for every type), `api/notifications.test.ts`.

## Delivery

### Branches

```
dev
 └── feat/notifications                  ← integration branch; holds the spec
      ├── feat/notifications-center      → PR 1 into feat/notifications
      ├── feat/notifications-realtime    → PR 2 (branched after PR 1 merges)
      ├── feat/notifications-email       → PR 3
      └── feat/notifications-scheduled   → PR 4
 feat/notifications → dev → main         ← one final PR once all 4 are in
```

- `feat/notifications` is created from `dev`. Its first commit is this spec (with the implementation adjustments above); no code.
- Each PR targets `feat/notifications`, never `dev`, so `main` never deploys half the feature (in particular the bump e-mail is never missing or duplicated between PR 1 and PR 3).
- The PRs are sequential: each sub-branch is created from `feat/notifications` after the previous PR merges.
- Merge `dev` into `feat/notifications` now and then to keep the final PR's conflicts small.
- CI only runs on pushes and PRs to `main`/`dev`. Either add `feat/notifications` to the `branches` filters of `ci-backend.yml` / `ci-frontend.yml` for the life of the feature (removed in the final PR), or run `./mvnw test` and `bun run lint && bun run build && bun run test` locally before each merge.

### PRs

Four vertical PRs (backend + frontend + docs each):

| PR | Branch | Scope | Result |
|----|--------|-------|--------|
| 1. Center | `feat/notifications-center` | V10, `domain/notification/` (entity, enum, `NotificationService`), events for every non-scheduled type, explicit deletes in `deleteDailyData` / `deletePelada`, list/count/read REST, bump `seenAt` ↔ `read_at`. Client: `api/notifications.ts`, `types/notification.ts`, `utils/notifications.ts`, bell and panel; counter fetched on load and `visibilitychange` | bell works, no realtime |
| 2. Realtime | `feat/notifications-realtime` | STOMP push after commit, interceptor `SUBSCRIBE`. Client: `RealtimeProvider`, `usePeladaChat` migrated, Attendance toasts | counter updates live |
| 3. E-mail and preferences | `feat/notifications-email` | preferences endpoints, e-mail pipeline with the dedicated executor and pacing, generic template, unsubscribe token and public endpoints, `List-Unsubscribe` headers, `DiaristaBumpedEmailListener` removed. Client: `/settings/notifications`, `/notifications/unsubscribe`, UserMenu item | e-mails |
| 4. Scheduled events | `feat/notifications-scheduled` | 15-min job for `SESSION_REMINDER` and `DIARISTA_WINDOW_OPEN` (`NOT EXISTS` guard, `Clock`), retention cleanup in the 04:00 job | reminders |

Between PR 1 and PR 3 the bump keeps sending its e-mail through the old listener; PR 3 retires it, so the e-mail is never missing or duplicated.

### Order inside each PR

**PR 1** touches about 8 services, so it is built in steps, each with its tests:
- [x] 1. V10, `Notification`, `NotificationType` (category + emailable), `NotificationCategory`, repository, `NotificationService` with the synchronous listener and the after-commit registration (no-op until PR 2), explicit deletes.
- [x] 2. Three types end to end, one per path: `DIARISTA_BUMPED` (attendance), `SESSION_CANCELED` (status), `ADDED_TO_PELADA` (membership).
- [x] 3. `NotificationController` (list, count, read, read-all) and the `bumps/seen` ↔ `read_at` sync.
- [x] 4. Client: types, api module, `utils/notifications.ts`, bell, panel. The bell works from here on.
- [x] 5. The remaining non-scheduled types, one service at a time (actor excluded, collapse of `TEAMS_SORTED` / `SESSION_FINISHED`, awards only on the first finalize).

Decisions taken while building PR 1:
- `AWARD_WON` is a system event (`actor` null): the admin who finalizes still gets their own award; `SESSION_FINISHED` skips them (rule 1).
- `GET /notifications` takes `?unread=true` so the "Não lidas" chip pages on the server (added to the REST table above).
- V10 creates both tables; `notification_preferences` has no entity until PR 3 (`validate` ignores unmapped tables).
- Events carry the entities the publishing service already loaded (`domain/daily`: `DailyCreatedEvent`, `DailyCanceledEvent`, `AttendanceChangedByAdminEvent`, `TeamsSortedEvent`, `DailyFinalizedEvent`; `domain/pelada`: `MembersAddedEvent`, `MembersRemovedEvent`, `AdminsChangedEvent`, `MensalistasChangedEvent`, `PeladaTransferredEvent`); the bump reuses `DiaristaBumpedEvent`. Only cancel (confirmed players) and create (members) read their recipients.
- The after-commit hook is `NotificationDelivery.deliver(ids)`, a no-op that PR 2 (push) and PR 3 (e-mail) fill in. The list itself is `NotificationInboxService`, so `NotificationService` stays the pipeline.
- Admin and mensalista changes only notify members whose status actually changed (both routes are idempotent). "Confirmar todos" notifies each newly confirmed member.
- `read-all` also sets `seenAt` on the bumps it reads, like reading one bump notification. In the client, reading a bump notification clears the Home alert (`AppLayout.clearBumps`), and dismissing the alert reads the notification (`markBumpRead`).
- Importing teams from a WhatsApp message (`populate`) doesn't send `TEAMS_SORTED`; only the sort does.
- The panel's gear to the preferences waits for PR 3 (the route doesn't exist yet); the UserMenu item stays "Em breve".
- `SESSION_REMINDER` text in the client uses the date ("Sessão de sáb, 31/10 às 20:00 na …"), not "amanhã", because the bell may be read days later.

**PR 2:** first extract the connection with backoff from `usePeladaChat` into `RealtimeProvider` with the chat behaving exactly as before (checked by hand), then add the `/user/queue/notifications` subscription, the server push and the toasts.
- [x] 1. `RealtimeProvider` in `AppLayout` (one connection, `subscribe` / `publish` / `connectionState` / `connectionId`), `usePeladaChat` on top of it. Checked by hand: send, receive, reconnect after restarting the API, the error queue, a single `/ws` connection.
- [x] 2. `JwtChannelInterceptor` accepts `SUBSCRIBE /user/queue/notifications`; `NotificationDelivery.deliver` calls `NotificationPushService.push` (`@Async`, default pool, failures only logged).
- [x] 3. Client: `useNotifications` subscribes, upserts by id, takes the pushed counter, refetches `unread-count` on every (re)connect, toasts with "Ver".

Decisions taken while building PR 2:
- The push payload is `NotificationPushDTO {notification, unreadCount}`, not the bare `NotificationDTO`: the client can't tell a collapsed notification (already counted) from a new one when it isn't in the loaded list, so the server sends the recipient's unread total (one grouped query per batch) and the client just applies it.
- The DTO assembly moved from `NotificationInboxService` to `NotificationDTOAssembler`, shared by the list and the push; the team query now takes several users (`TeamRepository.findTeamsOfUsersInDailies`).
- The old chat reconnect never worked: a dropped connection only fires stompjs's `onWebSocketClose` (not handled), and `client.activate()` is ignored while the client is still `ACTIVE`. The provider uses stompjs's own reconnect (`reconnectDelay` 1 s, `EXPONENTIAL`, max 4 s) and counts attempts in `onWebSocketClose`; after 3 failed retries it deactivates (`failed`, same chat banner as before) and starts over when the tab becomes visible again or the token changes. An API restart can take longer than the 7 s window, so the visibility resume matters.
- Toasts only for `DIARISTA_BUMPED`, `SESSION_CANCELED`, `ATTENDANCE_REMOVED_BY_ADMIN` and `SESSION_REMINDER` (`notificationToast` record); `ATTENDANCE_CONFIRMED_BY_ADMIN` is Attendance too but good news, so it only updates the bell.
- A pushed notification is only inserted into a list already loaded; otherwise the panel loads it on open.
- Not done here: a pushed `DIARISTA_BUMPED` doesn't refresh the Home card / "Você perdeu a vaga" alert (they still update on reload), and the server sends no STOMP heart-beats, so a silently dead connection (no close event) isn't detected.

**PR 3:** check the Resend plan first (the only external blocker). Write the exhaustive `NotificationEmailContent` `switch` and its "every emailable type renders" test before the dispatcher. Remove `DiaristaBumpedEmailListener` in the same commit that turns the new dispatch on.
- [x] 0. Resend plan checked: free (100/day, 3,000/month, shared with the password reset).
- [x] 1. `NotificationPreference` entity on the V10 table, `GET/PUT /notifications/preferences` (only differences from `defaultEmail` stored).
- [x] 2. `NotificationEmailContent` (exhaustive `switch`) and its "every type renders subject, title and CTA" test.
- [x] 3. `email/notification.html` + `NotificationEmail`, `EmailSender` headers, `UnsubscribeTokens`, public `unsubscribe` / `resubscribe` (permitAll, rate-limited, `JwtAuthFilter` skipped), `API_URL`.
- [x] 4. `NotificationEmailDispatcher` + `EmailPacer` wired into `NotificationDelivery`; `DiaristaBumpedEmailListener`, `DiaristaBumpedEmail` and `email/diarista-bumped.html` removed in the same commit.
- [x] 5. Client: `/settings/notifications`, `/notifications/unsubscribe?token=`, UserMenu item without "Em breve", the panel's gear.

Decisions taken while building PR 3:
- Resend stays on the free plan with the spec's defaults (no daily budget, no types cut): it only matters at roughly 30 peladas of 40 players. If traffic grows, the paid plan or the digest (phase 2) come next. Noted in BACKEND.md.
- The one-click URL points at the API, which only knew `APP_URL`: new `API_URL` setting (`app.api-url`), written to the deploy `.env` from the same value the frontend is built with (`vars.API_URL || APP_URL`).
- RFC 8058 one-click POSTs `List-Unsubscribe=One-Click` as a form to the header's URL, so the token can't be in a JSON body there: `POST /notifications/unsubscribe` takes `{token}` as JSON (the client's page) **or** `?token=` with any body (the mail provider). `/resubscribe` is JSON only.
- `JwtAuthFilter` returned 401 for any invalid Bearer token, even on public routes, and the client attaches the stored token and logs out on 401. It now skips the two e-mail link routes (`SecurityConfig.EMAIL_LINK_PATHS`), which never read the caller, so a visitor with an expired session isn't thrown to `/auth`. The one rate limit (10/min per IP) covers them; at scale, many one-click POSTs from the same provider IPs could hit it.
- The e-mail executor is owned by `NotificationEmailDispatcher`, not a bean: any `Executor` bean disables Spring Boot's default `applicationTaskExecutor`, which would have moved the push and the password reset e-mail onto the single e-mail thread.
- The pacer is one token every 500 ms with no burst (a 2-token bucket lets three sends into one second). 30 e-mails take ~15 s.
- The e-mails are built in a short read-only transaction and sent outside it, so pacing never holds a database connection. `EmailSender.enabled()` (false for the no-op) skips the whole build when there is no API key.
- `PUT /preferences` is partial (1–4 items): the client sends only the switch that changed; a value equal to the default deletes the row.
- `NotificationEmailContent` covers every type, emailable or not (the switch has no `default`); the CTA of `REMOVED_FROM_PELADA` opens `/home`. Category and award labels live in `NotificationCategory.label()` / `NotificationAward.label()`.
- Placeholders are replaced in one pass with every value HTML-escaped, so a pelada called `{{unsubscribeUrl}}` stays text.

**PR 4:** follow `DailySchedulerService` (try/catch per pelada) with an injected `Clock`; extend the 04:00 job with the retention deletes.
- [x] 1. Recipient queries in `NotificationRepository` (`findReminderRecipients`, `findWindowOpenRecipients`, each with the `NOT EXISTS` on the same recipient, type and session) and `NotificationRepositoryTest`.
- [x] 2. `ScheduledNotificationJob` (every 15 min, `Clock`, one `TransactionTemplate` per pelada in a try/catch), saving through `NotificationService.notifyScheduled`; `ScheduledNotificationJobTest` on H2.
- [x] 3. `NotificationRetentionJob` at 04:00 (two bulk deletes) and its boundary test.
- [x] 4. `DailySchedulerService` reads the `Clock` in each pelada's `timeZone` (known gap narrowed to `nextDaily`) and runs each pelada in its own transaction.

Decisions taken while building PR 4:
- No migration: V10's CHECK already accepts both types and `uq_notifications_scheduled` exists (`FlywayMigrationTest` still checks it).
- The guard is a JPQL recipient `SELECT … WHERE NOT EXISTS (same recipient, type, daily)` followed by `saveAll` through `NotificationService.notifyScheduled`, not a native `INSERT … SELECT`: IDENTITY gives the ids for `NotificationDelivery.deliver(ids)` after commit (no `RETURNING` on H2), and the existing build / save / after-commit path is reused. The job is the only writer of these types and `@Scheduled` runs it serially; the unique index stays the production backstop. Checked by hand that the idempotency test fails without the `NOT EXISTS`.
- Wide lookback: every `SCHEDULED` / `CONFIRMED` session with `diaristaOpensAt ≤ now < startsAt`, not only the windows opened since the last run, so nothing is lost while the API is down. A session created after its window had opened is skipped (`SESSION_CREATED` already told everyone and confirmations were open from the start). `Daily.createdAt` is a server-local `LocalDateTime` (`@CreationTimestamp`), read in the JVM zone that wrote it.
- Candidates come from one query with a coarse UTC date range (today − 1 … today + 2); the exact window is checked in Java with `AttendanceWindow`, so the pelada's zone and DST are respected. The window check runs before the pelada's transaction, so a bad zone fails only that pelada.
- `DIARISTA_WINDOW_OPEN` only while confirmed < capacity (checked when the window is processed; a session that fills before the run gets only the reminders). Recipients: every member without a mensalista row, creator and admins included.
- Retention is its own `NotificationRetentionJob` in `domain/notification` (same 04:00 cron as `PasswordResetTokenCleanup`, which stays in `auth`). No index on `created_at`: a daily scan of a small table.
- The scheduler's zone fix covers `DailySchedulerService` only; `nextDaily`'s "from today" stays in Known gaps.
- `DailySchedulerService.autoCreateDailies` ran every pelada in one `@Transactional`, so a database error in one pelada was caught but still rolled back the whole run (rollback-only). It now uses one `TransactionTemplate` per pelada, like the new job, and reloads the pelada by id inside it: the listed peladas are detached, and `SESSION_CREATED` reads the lazy `members` (`LazyInitializationException` otherwise). `DailySchedulerTransactionTest` runs it with no test transaction, which is how that case was caught.
- Resend quota as specified: `SESSION_REMINDER` e-mails by default (≈ one per unconfirmed mensalista per session; `maxMensalistas` defaults to 0), `DIARISTA_WINDOW_OPEN` doesn't.
- Client: nothing to change; `utils/notifications.test.ts` already covers the text, `/daily/:id` link and toast of both types.
- `docs/melhorias-escritas-a-mao.md` is untracked and stays out of this PR.

Docs to update in each PR:
- `core/docs/BACKEND.md`: `domain/notification/` in the package layout, entities, V10 row, "Notifications" API table, permission matrix (self only), Security (public unsubscribe routes + rate limit, new `SUBSCRIBE`), scheduled jobs, Resend quota note.
- `client/docs/FRONTEND.md`: new routes, `notifications.ts` module, `useNotifications` and `RealtimeProvider`, `components/notifications/`, `utils` tree, UserMenu without "Em breve".
- `docs/melhorias-escritas-a-mao.md`: remove "Adicionar sistema de notificações" with PR 4.

## Phase 2 (out of scope)

- Pelada invites (`PELADA_INVITE` with accept/decline in the panel) — next item of the improvements list.
- Chat mentions (@).
- Daily e-mail digest (if the Resend quota gets tight).
- Web Push / PWA.
- Mute per pelada.
- Unread count in the tab title.

## Risks

- Resend quota: free plan (checked in PR 3); enough at today's scale, next steps are the paid plan or the digest.
- STOMP broker and scheduled jobs assume a single API instance (already documented).
- Two text sources (client and e-mail), kept honest by the exhaustive `Record` / `switch` and their tests.
