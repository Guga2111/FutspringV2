# API: notifications and push

> The notification inbox, preferences and Web Push subscriptions. Part of the [backend guide](README.md).

All routes are under `/api/v1`. "Auth" is the relationship checked (see the matrix); the invitation-link preview and the e-mail preference links are also public (see below).

## Notifications (`NotificationController`, `/notifications`)
Every route acts on the caller's notifications only, except unsubscribe, which are public and act on the user and category signed in the e-mail's token.

| Method | Path | Body | Response |
|--------|------|------|----------|
| GET | `/notifications?page=0&size=20&unread=false` | — | `List<NotificationDTO {id, invitationId, type, category, award, createdAt, readAt, pelada {id, name, image}, daily {id, date, time}, actor (PublicUserDTO), team {name, color}, validUntil}>`, newest first; `size` clamped to 1–50, `page` ≥ 0; `unread=true` returns only unread ones. `team` is the caller's team, only on `TEAMS_SORTED`; `validUntil` only on `SUBSCRIPTION_ENDING` / `SUBSCRIPTION_PAST_DUE`. Two queries (page with fetch joins + the caller's teams of those sessions) |
| GET | `/notifications/unread-count` | — | `{count}` |
| POST | `/notifications/{id}/read` | — | 204; idempotent; missing or someone else's → 404. A `DIARISTA_BUMPED` notification also sets the bump's `seenAt` |
| POST | `/notifications/read-all` | — | 204; also sets `seenAt` on the bumps of the `DIARISTA_BUMPED` notifications it reads |

| GET | `/notifications/preferences` | — | `List<NotificationPreferenceDTO {category, emailEnabled, pushEnabled}>`: always the five categories (`SESSION`, `ATTENDANCE`, `MEMBERSHIP`, `AWARDS`, `BILLING`) with both channels, defaults applied |
| PUT | `/notifications/preferences` | `List<NotificationPreferenceDTO>` (1–5 items, `category` `@NotNull`; `emailEnabled` and `pushEnabled` optional, at least one per item, else 400 "Informe e-mail ou push"; a repeated category → 400) | the five categories after the change. Partial: only the categories and channels sent change; the row is deleted when the e-mail is back to its default and the push is null or equal to its default |
| POST | `/notifications/unsubscribe` | `UnsubscribeRequestDTO {token}` (JSON, from the client's page) **or** `?token=` with any body (RFC 8058 one-click from the mail provider, form body `List-Unsubscribe=One-Click`) | **public**, rate-limited; `NotificationPreferenceDTO` with `emailEnabled: false` (and the unchanged `pushEnabled`: the token only touches the e-mail); idempotent; a tampered token, a swapped category or an unknown user → 400 "Link inválido" |

Types and recipients (the actor is always left out): `DIARISTA_BUMPED` (bumped diarista), `SESSION_CANCELED` (confirmed players), `ATTENDANCE_CONFIRMED_BY_ADMIN` / `ATTENDANCE_REMOVED_BY_ADMIN` (the player; "Confirmar todos" included), `SESSION_CREATED` (every member; from the scheduler with no actor), `TEAMS_SORTED` (sorted or saved by hand; "Times definidos") / `SESSION_FINISHED` (players on the teams; collapsed while unread), `AWARD_WON` (each winner per award, first finalize only), `PELADA_INVITATION` (recipient, links to `/invitations/{invitationId}`, Membership e-mail preferences apply), `MEMBER_JOINED` (all current admins except the entrant, in-app only), `REMOVED_FROM_PELADA`, `ADMIN_GRANTED` / `ADMIN_REVOKED`, `MENSALISTA_GRANTED` / `MENSALISTA_REVOKED` (members whose status changed), `PELADA_TRANSFERRED` (new creator), `SUBSCRIPTION_PIX_CLAIMED` (every platform admin not deleted, the creator as actor, links to `/admin/peladas/{id}`). Billing (category `BILLING`, "Assinatura", e-mail and push on by default, links to `/pelada/{id}/assinatura`): `SUBSCRIPTION_ENDING` / `SUBSCRIPTION_PAST_DUE` from `SubscriptionNoticeJob` (see [Subscriptions](subscriptions.md#subscriptions-domainsubscription)), no actor, to the creator, with `validUntil` in the DTO ("vence em 07/12"; the lock date is `validUntil` + 7 days). Scheduled by `ScheduledNotificationJob` (every 15 min, no actor), for `SCHEDULED` / `CONFIRMED` sessions whose diarista window has opened (`AttendanceWindow.diaristaOpensAt`, start − 24 h in the pelada's zone) and that haven't started: `SESSION_REMINDER` (mensalistas who haven't confirmed) and `DIARISTA_WINDOW_OPEN` (every other member who hasn't confirmed, creator and admins included, only while confirmed < capacity). Each is created once per recipient and session. Every session in the window is picked, not just the ones that opened since the last run, so a run missed while the API was down is caught up; a session created after its window had opened is skipped (`SESSION_CREATED` already told everyone). Importing teams from a message (`populate`) doesn't notify.

E-mail: the emailable types go by e-mail when the recipient's category is on (see [Notification e-mails](notifications.md#notification-e-mails)). Not emailable: `ATTENDANCE_CONFIRMED_BY_ADMIN`, `TEAMS_SORTED`, `ADMIN_REVOKED`, `MEMBER_JOINED`.

Web Push: every type but `ADMIN_REVOKED` goes to the recipient's subscribed devices when the category's push preference is on (see [Web Push](notifications.md#web-push)).

Realtime: after the commit every new or collapsed notification is sent to its recipient on the STOMP user queue `/user/queue/notifications` as `NotificationPushDTO {notification: NotificationDTO, unreadCount}` (`unreadCount` is the recipient's unread total after it, so a collapsed notification isn't counted twice). Three queries per batch (rows with fetch joins, the recipients' teams of `TEAMS_SORTED`, a grouped unread count). Nothing is replayed: a client that was offline refetches `unread-count` when it reconnects.

## Push (`PushController`, `/push`)
The caller's Web Push subscriptions (one per browser/device). Every route reads the caller from the token.

| Method | Path | Body | Response |
|--------|------|------|----------|
| GET | `/push/vapid-public-key` | — | `VapidPublicKeyDTO {publicKey}` (base64url, the `applicationServerKey`), or 204 when push isn't configured |
| POST | `/push/subscriptions` | `PushSubscriptionRequestDTO {endpoint @NotBlank ≤ 1024, keys: {p256dh @NotBlank ≤ 200, auth @NotBlank ≤ 50}}` | 204. Upsert by `endpoint` (another account's endpoint moves to the caller, keys replaced); past 10 per user the least recently alive one is dropped. 400 for an endpoint outside the allowlist or keys of the wrong size (see [Web Push](notifications.md#web-push)); rate limited (10/min per IP) |
| POST | `/push/subscriptions/remove` | `RemovePushSubscriptionRequestDTO {endpoint @NotBlank ≤ 1024}` | 204; deletes it only when it is the caller's (someone else's or unknown: 204, nothing revealed). POST because a DELETE with a body is poorly supported |
