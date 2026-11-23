# Data layer

> Session and API client, the `src/api` modules, DTO types and authenticated photos. Part of the [frontend guide](README.md).

## Rules

1. **Only `src/api/*` imports `apiClient`**; each function returns `response.data`, typed. Request payload types live next to the function; response types in `src/types/`, with the backend's field names.
2. **The access token lives only in memory** (`lib/session.ts`); only `AuthContext` reads or writes `futspring_user`.
3. An API contract change updates the module, `src/types/*`, the zod schema, the table here and the backend in the same change.
4. Errors are read with `getErrorMessage(error, fallback)` (`lib/errors.ts`), or `applyServerErrors` in forms.
5. Status-like values are union types with `Record` maps.
6. Private photos load only through `useImageUrl` (authenticated blob, shared per account and file); never a token in an image URL, never in browser storage or the service worker cache.

## API client and auth

- Backend release P1 (2026-11-13): access JWTs are checked against the immutable account id and email together. Concurrent account edits may return 409 with the API message; existing form/toast error handling displays it. No request or response DTO changed.
- `src/api/client.ts` exports the single axios instance (`apiClient`). **Only `src/api/*` imports it**; never import `axios` or call `fetch` elsewhere.
- **Session** (spec `docs/history/2026-11-11-launch-hardening-spec.md`, B3): a short access JWT kept **only in memory** (`lib/session.ts`: `getAccessToken` / `setAccessToken`) and the refresh token in the `HttpOnly` cookie `futspring_refresh`, which the JS never reads (see [Tokens](../../core/docs/security.md#tokens-sdomainauthjwtservicejava)). `context/AuthContext.tsx` owns the session state: the user (`user`, also cached in `localStorage.futspring_user` to know on boot there is a session to restore), `restoring`, `login(token, user)` / `logout()` / `updateUser(patch)` (an edited profile merged into the stored user), read through `useAuth()`; the token is not in the context. Nothing else touches `futspring_user`. The old `localStorage.futspring_token` (7-day sessions) is no longer read and is removed on boot: those users log in once more.
- **Boot:** with a cached user, `refreshAccessToken()` (`POST /auth/refresh`) restores the session while `PrivateRoute` shows a skeleton. A 401 (no session, cookie expired or revoked) clears the user and goes to `/auth`; any other failure (an offline PWA, a 503 with Redis down) keeps the cached user: the screens show their error states and the next 401 tries again. Without a cached user no refresh is sent. Public pages read `user` to know someone is logged in (the landing redirect, the 404 button, `/join/:token`).
- `api/auth.ts` sends `withCredentials` (`WITH_SESSION_COOKIE`) only on the routes that set or read the cookie (login, refresh, logout, and `changePassword` in `api/users.ts`): in dev the API is another origin (5173 → 8080, same site). Login and the password change get the access token in the body and call `login`.
- `logout()` ends the session on the server (`logoutSession()`, `POST /auth/logout`, best effort: clears the cookie), drops the token and the cached user, and also releases this device's Web Push (best effort, never blocking the logout): clears the icon badge, sends `unsubscribePush(endpoint)` with the closing session's token and a 2 s timeout, then `subscription.unsubscribe()`. After a 401 (the session is already invalid) only the browser drops it; the server deletes the row at the push service's first 410. A 401 on that removal (a password reset or account deletion already revoked the token) is ignored by the interceptor, like `/auth/*`. Another account on the same device turns push on again with "Ativar notificações" (the permission is already granted, so no prompt).
- `installSessionInterceptors(apiClient)` (`lib/session.ts`, called when `AuthContext.tsx` loads, so the shell's first requests already go through it): the request interceptor adds `Authorization: Bearer <token>` from memory, unless the call set its own (the logout's push removal); on a **401** the response interceptor runs one refresh per tab (single flight: every request that got a 401 meanwhile waits for the same `POST /auth/refresh`) and repeats the request once with the new token. A refresh answering 401, or a repeat answering 401 again, ends the session (`onSessionExpired`: `AuthContext` clears it and hard-redirects to `/auth?expired=1&returnTo=…`); a refresh failing otherwise rejects with the original error and keeps the session. `/api/v1/auth/*` (a wrong password is a 401 the login form shows) and the push removal are left to the caller. Every refresh updates the session user (`onSessionRefreshed`); a refresh answered after a logout or new login is rejected, and old requests cannot retry or expire the new session.
- `RealtimeProvider` sets the STOMP `connectHeaders` in `beforeConnect` from `freshAccessToken()` (the current token, refreshed first when it expires within 30 s, read from the JWT's `exp`). The server closes each connection when the JWT used on CONNECT expires. The existing backoff reconnects, renews credentials as needed, and restores chat/error/notification subscriptions in `onConnect`; an HTTP refresh does not extend an already-open connection. Failed renewal cannot authorize an expired token and retries remain bounded. `e2e/realtime-expiration.spec.ts` exercises the actual browser provider with a simulated SockJS/STOMP server: successful renewal restores subscriptions; failed renewal stops after the existing retry budget. The chat and notifications share this connection.
- **Session generations:** Every non-exempt HTTP request retains its session generation. Logout or login invalidates pending refresh results, errors, responses and retries; an old 401 never expires a new login. Session changes abort the old refresh HTTP request so its late Set-Cookie cannot replace a new login cookie. New sessions have independent refresh flights; delayed 401s within one session reuse the current token. Session tests cover logout and account-switch interleavings.

## API modules (`src/api/*.ts`)

Each module exports typed async functions that return `response.data`, plus the request payload types (`CreatePeladaData`, `MatchResultInput`, `PopulateDailyInput`…). Response types live in `src/types/` and mirror the backend DTOs in the [backend API reference](../../core/docs/README.md#api-reference).

| File | Endpoints |
|------|-----------|
| `auth.ts` | `/api/v1/auth/register` (`RegisterRequest` with `termsVersion`), `/api/v1/auth/login`, `/api/v1/auth/forgot-password` (`requestPasswordReset`), `/api/v1/auth/reset-password` (`resetPassword`, `ResetPasswordData`), `/api/v1/auth/refresh` (`refreshSession`, called only through `lib/session.ts`'s single flight) and `/logout` (`logoutSession`); login, refresh and logout send `withCredentials` (`WITH_SESSION_COOKIE`, the `futspring_refresh` cookie; see [Tokens](../../core/docs/security.md#tokens-sdomainauthjwtservicejava)) |
| `peladas.ts` | `/api/v1/peladas` (my, detail, create, update, delete, image, players, admin, member stars (`setPlayerStars`), mensalista (`setMensalista`), bulk member actions (`setAdmins`, `setMensalistas`, `setPlayersStars`, `removePlayers`, POST `/players/remove`), remove image (`removePeladaImage`), transfer (`transferPelada`, PUT `/creator`), ranking, attendance (`getPeladaAttendance`), awards, member stats, member history), `/api/v1/users/search` (`PublicUser`, q ≥ 3 chars: part of the name or the full e-mail) |
| `dailies.ts` | `/api/v1/peladas/{id}/dailies`, `/api/v1/dailies/{id}` (detail, confirm, bumps seen (`markBumpsSeen`), admin confirm, confirm all, sort/swap teams (`TeamDTO[]`), save teams by hand (`saveTeams`, PUT `/teams`, `SaveTeamsInput`), team name/color, status, results, finalize, populate, champion image, delete) |
| `users.ts` | `/api/v1/users/{id}` (profile, update, image, stats, timeline, matches, peladas in common), `/api/v1/users/me/password` (`changePassword`, answers a new `AuthResponse`), `/me/deletion-blockers` (`getDeletionBlockers`), `/me/delete` (`deleteAccount(password)`, POST), `/me/export` (`exportMyData`: a `Blob` with `responseType: "blob"`; the client only saves the file, so the DTO has no type in `src/types`) |
| `chat.ts` | `/api/v1/peladas/{id}/messages` (paged history) |
| `finance.ts` | `/api/v1/peladas/{id}/finance/**` (see [Financeiro](finance.md)); `getFinanceMovements(id, page, {from, to})` and `getFinanceAudit(id, page, {from, to, operations})` send only the filters that are set (`FinanceHistoryFilter`, ISO dates), with `paramsSerializer: { indexes: null }` so `operations` repeats (`?operations=RECEIVE&operations=REPORT`); `adjustFinanceCharges(id, listId, command, chargeIds, {components, adjustment})` (POST `/lists/{listId}/charges/adjust`) |
| `tours.ts` | `/api/v1/tours` (`getTourState`: the caller's seen tours), `/{tour}/seen` (`markTourSeen`, idempotent) |
| `push.ts` | `/api/v1/push/vapid-public-key` (`getVapidPublicKey`, 204 → `null`), `/subscriptions` (`subscribePush`, the parsed `PushSubscription.toJSON()`), `/subscriptions/remove` (`unsubscribePush(endpoint, {token, timeout})`: the logout sends the closing session's token, since the stored one is already gone) |
| `platform.ts` | `/api/v1/platform/**` (platform admin): `getPlatformOverview`, `getPlatformPeladas(page, {state, q})`, `getPlatformPelada`, `recordPlatformPayment` (`RecordPaymentBody`), `changePlatformSubscription` (`ChangeSubscriptionBody`), `voidPlatformPayment(paymentId, reason)`, `getPlatformPayments(page, {from, to})`, `getPlatformUsers(page, q)`; lists send only the filters that are set, pages of `PLATFORM_PAGE_SIZE` = 20 |
| `subscription.ts` | `/api/v1/peladas/{id}/subscription` (`getPeladaSubscription`, creator only), `/pix-claim` (`claimPix(peladaId, {plan})`) |
| `clientErrors.ts` | `/api/v1/client-errors` (`sendClientError(report)`, `ClientErrorReport` mirroring `ClientErrorRequestDTO`; public, 204) |
| `notifications.ts` | `/api/v1/notifications` (`getNotifications({page, size, unread})`, pages of `NOTIFICATIONS_PAGE_SIZE` = 20), `unread-count` (`getUnreadNotificationCount`), `{id}/read` (`markNotificationRead`), `read-all` (`markAllNotificationsRead`), `preferences` (`getNotificationPreferences`, `updateNotificationPreferences`: partial, send only the changed category; items are `NotificationPreferenceChange` with `emailEnabled` and/or `pushEnabled`, the response carries both channels (the Push column of the settings comes with the web push client)), the public `unsubscribe` (`unsubscribeFromEmails(token)`); re-enable via authenticated preferences |

When an endpoint or DTO changes, update the module, `src/types/*` and this table in the same change as the backend.

## Types

- `src/types/*` — API response DTOs, matching the backend field names exactly. `PeladaResponse.isFinanceManager` (the caller is a responsável financeiro; `/peladas/my` and `/users/{id}/peladas`) drives the mobile "+" menu's Nova cobrança. The backend uses Java records; boolean flags remain `isAdmin`, `isFinished`, `isConfirmed`, and `isMensalista`. PR4 removes only their unused duplicate aliases `admin`, `finished`, `confirmed`, and `mensalista`; client payloads, types, schemas, and behavior are unchanged. Omitted create-pelada settings still default to 2 teams / 5 players per team; partial updates still leave null or omitted fields unchanged.
- Deleted accounts: `PublicUser`, `PlayerDTO` and the chat `SenderDTO` carry `isDeleted`; such a player is named "Ex-jogador" by the API and drawn with `PlayerAvatar deleted` (the neutral avatar). They never appear in current lists (members, rankings), so the profile links there stay as they are.
- `src/api/*` — request payload types next to the function that sends them.
- Component prop types and view models stay next to the component/hook that owns them.
- Status values use a union type and typed maps (`types/daily.ts`, `DailyStatusBadge`; `invitationStatusLabel` / `invitationStatusStyle` in `types/invitation.ts`; `financeChargeStatusLabel` / `financeChargeStatusStyle` and `financeListStatusStyle` in `types/finance.ts`); do the same for new enum-like fields:

```ts
export type DailyStatus = "SCHEDULED" | "CONFIRMED" | "IN_COURSE" | "FINISHED" | "CANCELED"

export const dailyStatusLabel: Record<DailyStatus, string> = {
  SCHEDULED: "Agendada",
  CONFIRMED: "Confirmada",
  IN_COURSE: "Ao vivo",
  FINISHED: "Finalizada",
  CANCELED: "Cancelada",
}
```

`Record<Enum, …>` makes TypeScript fail when a value is added, instead of `switch`/`if` chains spread across components.

## Authenticated photos

`api/files.ts` GETs `/api/v1/files/{encodedFilename}` as a Blob through the existing Bearer/refresh interceptor. `hooks/useImageUrl` returns `{url, ref}`: attach `ref` to the photo frame/fallback. IntersectionObserver starts the read within 200 px of the viewport; CSS-hidden thumbnails do not load. `lib/privateImages` shares one authenticated request/object URL per account+filename among mounted consumers, aborts and revokes on final release, and ignores late replies. Account/image changes switch the subscription without exposing the old bytes. Player/pelada avatars, banners, champion heroes and history/gallery use it; no JWT is put in an image URL. Failed/forbidden photos use existing fallback states. Service worker/API caches never store these bytes. Invitation previews omit private photos. The privacy notice matches self/shared-group profiles and member-only group/champion photos.

Private image tests cover deduplication, account separation, final release, abort races and fallback/retry after remount. Playwright `e2e/private-photos.spec.ts` checks offscreen history and CSS-hidden mobile thumbnails, then verifies shared authenticated avatars in light/dark at 375/1280 px. Full-page visual snapshots scroll existing frames into view before capture to exercise IntersectionObserver. No private image is written to browser persistence or service-worker caches.

## Antipatterns

Don't introduce these. Numbers are shared by the whole frontend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 6 | Casting errors inline (`err as { response?: { data?: { message?: string } } }`) or copying an `extractErrorMessage` per file | One `getErrorMessage(error, fallback)` in `lib/errors.ts`; `applyServerErrors` for forms | — |
| 14 | Reading/writing `futspring_user` outside AuthContext, or keeping the access token anywhere but `lib/session.ts` (memory) | `useAuth()`, `getAccessToken()` | — |
| 15 | Importing `axios` or calling `fetch` outside `src/api` | Add a function to an `api/*` module | — |
| 16 | Hardcoding the backend URL | `import.meta.env.VITE_API_URL` through `apiClient` | — |
| 18 | Status/position magic strings compared across components | Union types + `Record` maps in `types/` | — |
