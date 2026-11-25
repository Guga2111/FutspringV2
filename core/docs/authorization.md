# Ownership and authorization

> The relationship check pattern, the permission matrix, rules for new code and known gaps. Part of the [backend guide](README.md).

## Ownership & authorization

There are no roles; permissions come from the caller's relationship with the resource. **Every route that takes an id must check that relationship in the service.** The frontend's admin checks only hide UI.

### The pattern

```java
// DailyService.updateStatus
@Transactional
public DailyListItemDTO updateStatus(Long id, DailyStatus newStatus, String currentUserEmail) {
    User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);    // 1. who is calling
    Daily daily = findDaily(id);                                            // 2. load the resource (404)
    accessHelper.requireAdmin(daily.getPelada(), caller);                   // 3-4. walk up to the pelada, check (403)
    ...                                                                     // 5. only then act
}
```

Relationships: **creator** (`pelada.creator`), **admin** (`pelada.admins`), **member** (`pelada.members`), **self** (`user.email == caller email`).

### Permission matrix

| Action | Required |
|--------|----------|
| Create pelada | any authenticated user (becomes creator, member, admin and responsável financeiro) |
| Accept an invitation / join by link | the recipient / any authenticated user (not blocked); a `TRIAL`/`PENDING` pelada takes up to 30 members (409), a read-only one nobody new (402) |
| Read pelada detail, dailies, daily detail, ranking, attendance ranking, awards, member stats/history, chat history, send chat, subscribe to the chat topic | member (member stats/history: the target user must also be a member, else 404) |
| Update pelada (teams and players per team included), upload/remove pelada image, invite/remove player, set/unset admin | admin (the creator can't be removed or demoted; remove/set admin need the target to be a member, else 404). Remove and set admin also work on a list of members: one request, one transaction, any invalid target fails the whole batch |
| Set a member's stars in the pelada | admin; any member, the creator and the caller included (target must be a member, else 404); one member or a list (atomic) |
| Mark/unmark a member as mensalista | admin; any member, the creator and the caller included (target must be a member, else 404; 409 past `maxMensalistas`); one member or a list (atomic: a batch that doesn't fit changes nobody) |
| Delete pelada (cascades dailies, results, teams, attendance, messages, rankings; rebuilds global Stats) | creator; blocked permanently after any published financial list or cash movement |
| Transfer the pelada (`PUT /peladas/{id}/creator`) | creator; the target must be an admin of the pelada; the old creator stays admin |
| Create daily, change status, delete daily, sort/swap teams, save teams manually, submit results, finalize, populate, champion image | admin of the daily's pelada; writable subscription (else 402, see [Subscriptions](subscriptions.md#subscriptions-domainsubscription)), except cancelling the session |
| Confirm attendance (self) | member; diaristas only inside their window and while there are spots (see [Attendance](domain-model.md#attendance-mensalistas-and-diaristas)); writable subscription (402) |
| Dismiss the bump alert (`bumps/seen`) | member; only the caller's own bumps |
| Unconfirm (self) | must be confirmed (also in a read-only pelada) |
| Confirm/unconfirm another player, confirm every member | admin, target must be a member; no window, no capacity, never bumps; confirming needs a writable subscription (402), unconfirming doesn't |
| Rename team | member **and** player on that team (team must belong to the daily); writable subscription (402) |
| Change team color | member and (team player or admin); writable subscription (402) |
| Any finance write (`/peladas/{id}/finance/**` POST/PUT) | see [Finance permissions](finance.md#finance-permissions-and-filtering); writable subscription (402) |
| Update profile, avatar, background | self |
| Change password, read deletion blockers, delete the account, export the account's data (`/users/me/*`) | self only: the caller comes from the token, the routes take no `{id}`; the current password is re-checked for the change and the deletion |
| View a profile (`/users/{id}`) | self or a user sharing a pelada (403 otherwise); `email` only for self; a deleted account → 404 (also on `/stats*` and `/peladas`) |
| User stats, timeline, match history (`/users/{id}/stats…`) | self, or a user who shares a pelada (403 otherwise); non-self viewers only get sessions of the peladas they share |
| Peladas in common (`/users/{id}/peladas`) | any; returns only peladas both users belong to |
| The pelada's subscription (`GET /peladas/{id}/subscription`, `POST …/pix-claim`) | creator only (admins and members 403); the claim works in a read-only pelada, not in an exempt one (409) |
| Platform admin (`/platform/**`: overview, peladas and their subscriptions, payments, users) | `users.platform_admin` (set by SQL only); anyone else gets 404 "Recurso não encontrado", as for an unknown route |
| Search users | any; public fields only (`PublicUserDTO`), query ≥ 3 characters |
| List / count / read notifications, e-mail preferences (`/notifications/**`) | self only: every route reads the caller from the token and takes no `userId`; someone else's notification id → 404 |
| Read / mark the guided tours seen (`/tours/**`) | self only: the caller comes from the token; no route takes a `userId` |
| Unsubscribe from an email (`POST /notifications/unsubscribe`) | public: signed user/category/version/expiry, 30 days; expired/revoked/invalid →400. Re-enable via authenticated PUT /notifications/preferences only |
| Web Push subscriptions (`/push/**`) | self only: the owner comes from the token, no route takes a `userId`; removing someone else's endpoint is a silent no-op |

### Rules for new code

1. Resolve the caller from the token, never from the body or a path param. Ignore any `userId`/owner field the client sends for "who am I".
2. Every `{id}` route checks the relationship before doing anything else.
3. **Scope child ids to the parent in the path.** A `matchId`, `teamId` or `userId` in the body/path must be checked to belong to the `dailyId`/`peladaId` of the route (e.g. `team.getDaily().getId().equals(dailyId)`, as `updateTeamName` does). Otherwise an admin of one pelada can touch another pelada's data.
4. Use `PeladaAccessHelper` (exists queries) instead of loading `members`/`admins` collections just to call `contains`.
5. Don't expose email or other PII outside the pelada context; public user views return id, username, image, position.
6. 403 for "exists but you can't", 404 for "doesn't exist". When even revealing existence matters, answer 404 for both.
7. Each new `{id}` route gets tests for: not a member (403), member but not admin (403 where admin is required), and a child id from another pelada/daily.
8. Every command that writes a pelada's sporting or financial data calls `SubscriptionGuard.requireWritable(pelada)` right after its permission check (finance writes get it from `FinanceTransactions.execute`), and gets a 402 case in `SubscriptionReadOnlyTest`. A job that writes them skips peladas where `isWritable` is false.

### Known gaps (fix when touching these areas)

| Gap | Where |
|-----|-------|
| No unique constraint on `user_daily_stats(daily_id, user_id)` / `player_match_stats(match_id, user_id)` (the code never writes duplicates, but the database doesn't enforce it). Check production for duplicates before adding them | `V1__baseline.sql` |
| A logout ends the refresh session, not the access token already issued: it stays valid until it expires (`JWT_EXPIRATION_MS`; the default is still 7 days, production sets 15 min through the GitHub variable) | `JwtService`, `JWT_EXPIRATION_MS` |

## Antipatterns

Don't introduce these. Numbers are shared by the whole backend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 2 | Trust a child id from the request without checking it belongs to the route's parent (IDOR) | Scope it: `match.getDaily().getId().equals(dailyId)` or a scoped load (the parent's children by id, as `submitResults` does with the session's matches) | — |
| 3 | Endpoints that read another user's data with no relationship check, or expose email | Check the relationship; return a public DTO without PII (`PublicUserDTO`) | — |
