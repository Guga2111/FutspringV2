# Security

> Filter chain, tokens and refresh sessions, CORS, rate limits, WebSocket, password reset, e-mail verification, uploads and private files. Part of the [backend guide](README.md).

## Rules

1. Authorization is in services; the filter chain only authenticates. A new public route is listed in `SecurityConfig`; if it never reads the caller, it also goes in `JwtAuthFilter.shouldNotFilter`, so a stale `Authorization` header doesn't answer 401.
2. Tokens are verified only through `AccessTokenVerifier` (signature, user id, e-mail, `tokenVersion`).
3. Secret tokens are 256 random bits; only their SHA-256 is stored.
4. Revoking sessions increments `tokenVersion`, ends the refresh sessions and deletes the user's push subscriptions.
5. A new rate limit goes into `AuthRateLimitFilter`, keyed by the route's logical name on the decoded path.
6. Uploads are decoded from their content and re-encoded by `FileUploadService`; files are read only through `FileReadService`, which checks the relationship.

## Filter chain (`S/shared/config/SecurityConfig.java`)

- Two chains. `actuatorFilterChain` (`@Order(1)`, `EndpointRequest.toAnyEndpoint()`) permits every actuator endpoint: they are only served on the management port 9091, which Docker never publishes (Boot applies the security filter to that port too, so without this chain Prometheus would get 401). `filterChain` (`@Order(2)`) is everything else.
- CSRF disabled, stateless sessions, JSON 401 entry point.
- Public: `/api/v1/auth/**`, `/ws/**` (the STOMP layer authenticates itself, see WebSocket), `GET /invite-links/{token}` (restricted invitation preview), `GET /livez` / `/readyz` (probes, `https://futspring.luisgosampaio.com/api/readyz` through nginx) and `POST /api/v1/notifications/unsubscribe` (`SecurityConfig.EMAIL_LINK_PATHS`, authorized by the HMAC token in the request) and `POST /api/v1/client-errors` (`SecurityConfig.CLIENT_ERRORS_PATH`, the web app's error reports). With `API_DOCS_ENABLED=true` (dev only), `GET /v3/api-docs/**`, `/swagger-ui/**` and `/swagger-ui.html` too; otherwise they aren't served and fall under the authenticated rule. Everything else requires a valid token.
- `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`. It accepts a Bearer token only through `AccessTokenVerifier`: valid signature, not expired, the user still exists and the immutable `userId` and email identify the same verified, non-deleted row and the `ver` claim equals `users.token_version` (one indexed query per authenticated request). Otherwise it returns 401 on **any** path, including the public ones, except `EMAIL_LINK_PATHS`, `CLIENT_ERRORS_PATH` (an error with an expired session must still arrive), `SESSION_COOKIE_PATHS` (`/auth/refresh` and `/auth/logout`: they read the refresh cookie and are called with an expired access token) and public invitation preview GETs, where it ignores the header (`shouldNotFilter`): a visitor whose stored session expired must still be able to use the e-mail's link, and those routes never read the caller.
- No roles: authorities are always empty, there is no `@PreAuthorize`/`@EnableMethodSecurity`. **All authorization is done in services** (see [Ownership](authorization.md#ownership--authorization)).
- Passwords: BCrypt (strength 10).

## Tokens (`S/domain/auth/JwtService.java`)

- HS256 JWT, claims `sub` = email, `userId`, `email`, `ver` (the user's `tokenVersion`; tokens without it count as 0), `iat`, `exp`; TTL 7 days by default.
- Returned in the body (`AuthResponseDTO {token, user}`); the web app keeps it only in memory and renews it with `POST /auth/refresh` on boot and on a 401 (see [API client and auth](../../client/docs/data-layer.md#api-client-and-auth)). The access TTL is `JWT_EXPIRATION_MS`: 15 min (`900000`, the GitHub variable) once the cookie client is in production; the default stays 7 days.
- Revocation is per user: incrementing `User.tokenVersion` (a password reset, a password change, the account deletion) invalidates every access token and every refresh session issued before; a deleted user's tokens stop working too.

**Refresh sessions** (`domain/auth/session`, `RefreshSessionService`, stored in Redis):
- Verified login and the password change open a session and set the refresh token in the cookie `futspring_refresh` (`SessionCookie`): `HttpOnly; Secure; SameSite=Strict`, `Path` = the path of `API_URL` + `/api/v1/auth` (production `/api/api/v1/auth`, because nginx strips `/api`; dev `/api/v1/auth`; 5173 and 8080 on `localhost` are the same site, so `Strict` works in dev), `Max-Age` = the session's TTL. `Secure` comes from `AUTH_COOKIE_SECURE` (only the dev compose turns it off). The token is 256 random bits (`SecretTokens`); only its SHA-256 is stored.
- Keys: `rt:{sha256(token)}` → `{userId, sessionId, tokenVersion}` and `sess:{sessionId}` → `{userId, createdAt, absoluteExpiresAt, lastUsedAt}` (JSON), both with TTL = min(30 days, `absoluteExpiresAt` − now); each refresh renews the session's TTL (30 days sliding), never past `absoluteExpiresAt` (opened + 90 days, also checked against the application clock).
- `POST /auth/refresh`: valid when `rt` and `sess` exist, the session hasn't passed its ceiling and the stored `tokenVersion` equals the user's (one `findById`; a deleted account fails too). Answers a new access token and a **new** refresh token; the previous one keeps at most a one-minute grace (never extending its remaining TTL) (no reuse detection: two tabs, or the PWA and the browser, refreshing together must not log anyone out). Needs an `Origin` header listed in `ALLOWED_ORIGINS` (403 otherwise: the app and the API share an origin in production, so CORS alone wouldn't check it). Invalid → 401 "Sessão expirada. Entre novamente." clearing the cookie. The `Authorization` header is ignored.
- Rotation checks the old token and session still exist and writes both renewed keys in one Redis Lua operation. Only a login/password change can create a session; a refresh racing logout can never recreate it. Logout ordered after a rotation invalidates its new token too.
- `POST /auth/logout`: deletes the session of the cookie (every refresh token of it stops; the user's other sessions stay) and clears the cookie; idempotent, 204. The access token of that session keeps working until it expires (up to `JWT_EXPIRATION_MS`, 15 min in production).
- **Redis down:** verified login and password change still answer the access token, without a cookie (`futspring.session.open{result=error}`, logged); refresh answers 503 "Não foi possível renovar a sessão agora…" keeping the cookie (`futspring.session.refresh{result=error}`), so the client can tell an outage from an ended session; logout clears the cookie and leaves the session to expire. Both counters feed the "Sessões (Redis)" alert.

## CORS

- One list, `ALLOWED_ORIGINS` (comma-separated, property `app.cors.allowed-origins`), read by `CorsConfig`. Its `CorsConfigurationSource` is applied by Spring Security (`http.cors`), and `WebSocketConfig` uses the same origins for `/ws`.
- Production sets it to `APP_URL` (`deploy.yml`); the dev default is `http://localhost:5173`.
- `allowCredentials(true)`, so a cross-origin dev setup (5173 → 8080) sends the refresh cookie when the client asks for it (`withCredentials`). The same list gates `POST /auth/refresh` by its `Origin` header.

## Rate limiting

Invitation quotas and public preview limits are documented in [Membership invitations](api-peladas.md#membership-invitations-peladainvitationcontroller).

`AuthRateLimitFilter` (in the security chain): 10 requests per minute per client IP on each of `POST /api/v1/auth/login`, `/register`, `/forgot-password`, `/reset-password`, `/verification-email`, `/verify-email`, the logged-in password checks `POST /api/v1/users/me/password` and `/users/me/delete`, the Web Push subscription `POST /api/v1/push/subscriptions` (each one can cause future sends), and the e-mail link routes `/api/v1/notifications/unsubscribe` , and the account export `GET /api/v1/users/me/export` (it reads everything the account has), 30 per minute on the session refresh `POST /api/v1/auth/refresh` (logout is not limited), and 20 per minute on the web app's error reports `POST /api/v1/client-errors`, answering 429 with the usual error body. Routes are matched with `PathPatternRequestMatcher` on the decoded path (the mechanism Spring Security and MVC use), never the raw `getRequestURI()`, so `/api/v1/auth/%6Cogin` hits the login's limit. Each route has a logical name (`login`, `register`, `forgot-password`, `reset-password`, `change-password`, `delete-account`, `push-subscribe`, `unsubscribe`, `export`, `refresh`, `client-errors`, `invite-preview`); the bucket key is `<name>:<ip>`, never the URI received, and the name is the `route` tag of `futspring.ratelimit.rejected`. Buckets live in a Caffeine cache (max 100k entries, evicted 10 min after the last access). The IP is `getRemoteAddr()`, set by Tomcat's `RemoteIpValve` from `X-Forwarded-For` (see Configuration: right to left, trusted proxies skipped), and nginx overwrites that header with the real address (`ops/nginx/futspring.conf`). `AuthRateLimitFilterTest` runs against the real server: encoded paths, one bucket for plain and encoded, injected `X-Forwarded-For` values. In memory: one API instance only. New rate limits go into a filter like this one.

## WebSocket (`S/domain/auth/JwtChannelInterceptor.java`)

- `CONNECT` needs `Authorization: Bearer <token>` in the STOMP headers, checked by `AccessTokenVerifier.verifyConnection` using the same account/signature/version checks as HTTP, plus a required signed `exp`. Only the verified email and expiration are retained, never the raw JWT. `StompSessions` registers one deadline per transport session and schedules closure on the application `taskScheduler` (resolved lazily by name, separate from broker heartbeats). A connection closes with `POLICY_VIOLATION` when its JWT expires, even while idle; reconnecting requires valid credentials. An HTTP refresh does not extend an existing connection's deadline. Immediate logout revocation remains outside this behavior.
- `StompSessions` also guards inbound MESSAGE/SUBSCRIBE and outbound MESSAGE/CONNECT_ACK on both `preSend` and `ExecutorChannelInterceptor.beforeHandle`, so queued application traffic is rechecked at execution. At `now >= exp`, or with missing authenticated state, application traffic is dropped for that session, including private notifications. The guard denies traffic even if scheduling is late or transport closure fails. It cannot retract traffic already authorized and handed to the transport before the deadline. Tasks are canceled on disconnect, revocation, setup failure and shutdown; a stale callback only references its original transport. Scheduling failure rejects connection authentication.
- Explicit revocation still happens after commit through `StompSessions` (the open transports come from its decorator; the user's sessions/subscriptions from Spring's `SimpUserRegistry`):
  - **Member removed** (`MembersRemovedEvent`): their subscriptions to `/topic/pelada/{id}` are cancelled with an `UNSUBSCRIBE` sent to the broker. The connection stays open (the bell keeps working, `REMOVED_FROM_PELADA` arrives live); closing it would make a client still on the pelada page reconnect and be refused in a loop. A send to the chat already gets 403 from `ChatService`. A removed and re-invited member gets the topic back when the chat subscribes again (opening the pelada or reconnecting).
  - **Tokens revoked** (`TokensRevokedEvent`, published with the `tokenVersion` increment of a password reset, a password change and the account deletion, with the e-mail the sessions were opened with): every connection of the user is closed (`POLICY_VIOLATION`); the client reconnects and its old token is refused at CONNECT (the password change's own device reconnects with its new token).
  - A rollback changes nothing (`@TransactionalEventListener`, after commit).
- `SUBSCRIBE` is allowed only to `/topic/pelada/{id}` when the user is a member of that pelada (`existsByIdAndMembers_Email`), and to the user's own `/user/queue/errors` and `/user/queue/notifications` (`/user/...` resolves to the session's principal, so nobody can subscribe to another user's queue). Any other destination is rejected.
- `SEND` needs an authenticated session and a destination under `/app/`; a frame sent straight to a broker prefix (`/topic`, `/queue`, `/user`) is rejected, so every message goes through `ChatController` → `ChatService`.
- Clients send to `/app/pelada/{peladaId}/send` (`SendMessageRequest {content}`); the server broadcasts `MessageDTO` to `/topic/pelada/{peladaId}`. Errors (`AppException` from `ChatService`) go back to the sender on `/user/queue/errors` as `{status, message, timestamp}`.
- Notifications are pushed to `/user/queue/notifications` (see [Notifications](api-notifications.md#notifications-notificationcontroller-notifications)). The web app opens one connection per logged-in user and shares it between the chat and the notifications.

## Password reset

- `POST /auth/forgot-password` always answers 204 and does the same work whether or not the e-mail has an account (no user enumeration). For an existing user it invalidates the pending tokens, stores the SHA-256 of a new 256-bit token (30 min) and publishes `PasswordResetRequestedEvent`; at most one e-mail per user per minute.
- The e-mail is `src/main/resources/email/password-reset.html` (table layout + inline styles + Outlook conditionals, adapted from a Stripo template; placeholders `{{appUrl}}`, `{{link}}`, `{{ttlMinutes}}`, `{{username}}`, the username HTML-escaped). The logo (`resources/email/futspring-logo.png`, 160 px) travels inside the e-mail as a Resend inline attachment (`InlineImage`, `content_id` → `<img src="cid:futspring-logo">`), so it shows locally too and doesn't depend on the web app being deployed.
- `PasswordResetEmailListener` sends the e-mail with Resend **after the commit** and on another thread (`@Async @TransactionalEventListener`), with the link `${app.frontend-url}/reset-password?token=…`. A Resend failure is only logged (Spring's async handler); the user asks again.
- `POST /auth/reset-password` finds the token by hash with a row lock (unused, not expired), sets the new BCrypt password and marks every pending token of the user as used. Any invalid token answers 400 "Link inválido ou expirado. Peça um novo.".
- The reset increments `User.tokenVersion`, so every session opened before (other browsers, a stolen token) gets 401 on its next request and the frontend sends it to `/auth`. It also deletes every Web Push subscription of the user in the same transaction, so the old devices stop receiving.
- `PasswordResetTokenCleanup` deletes tokens expired or used more than a day ago, daily at 04:00.

## Uploads

`FileUploadService` handles every public image (avatar, background, pelada image, champion photo) through `ImageDecoder` (`domain/file`):

- **Accepted:** at most 5 MB; the format comes from the content, never from the client. Anything ImageIO can't read (text, HTML, SVG) → 400 "Imagem inválida"; a format other than JPEG / PNG / WebP → 400; a declared `Content-Type` different from the real format → 400 "O conteúdo da imagem não corresponde ao tipo informado"; more than 20 million pixels → 400, read from the header before any pixel is decoded.
- **Memory:** a large image is never decoded at full size. The reader subsamples while reading down to at most twice `FileUploadService.MAX_SIDE` (2048 px) on the long side, then scales smoothly (bilinear) to fit 2048 px. A 20-Mpx photo allocates ~87 MB (503 MB before), a 12-Mpx one ~137 MB.
- **Concurrency:** at most 2 images are decoded and re-encoded at once in the whole server (`ImageDecoder.withPermit`, public and finance uploads together); one more gets **503** "Muitas imagens sendo enviadas agora. Tente de novo em instantes." at once instead of waiting, because the callers hold a transaction and a database connection.
- **Re-encoding:** the EXIF orientation is applied, then the image is re-encoded from its pixels: JPEG (quality 0.9) without alpha, PNG with alpha. Nothing of the original survives (no EXIF / GPS, XMP, ICC profile or appended bytes). The stored name is a UUID plus the extension of the re-encoded format (`.jpg` / `.png`; older `.webp` files are still served). Files uploaded before this check keep whatever metadata they had.
- **Lifecycle:** a new file is removed if the transaction rolls back; a replaced file is deleted after the commit (`deleteImageAfterCommit`). Deleting a pelada deletes its image and every session's champion photo (`PeladaService.deletePelada` → `DailyService.deleteDailyData`); deleting a session deletes its champion photo.

## Private files

Every `GET /files/{filename}` goes through `FileReadService`, which checks the current database ownership and relationship **before** touching the disk; a filename alone grants nothing.

- Player images and backgrounds: self or a shared pelada. Pelada and champion images: current membership.
- Missing, unrelated and orphan files all answer 404; responses are `no-store` and `nosniff`; the path-traversal guard rejects anything outside the uploads directory.
- Leaving a pelada revokes future reads (another shared pelada can still authorize a player's photo); copies already downloaded can't be revoked.
- Profile reads enforce the same self / shared-pelada relationship. Public invitation previews return `image: null`; static marketing assets are separate.

## Client error redaction

`ClientErrorRedaction` sanitizes every untrusted field of `POST /client-errors` (path, message, stack, release, user agent) before logging, independently of the browser's own redaction (a forged report gets the same treatment):

- Removed: invitation token segments (`/join/{token}`, `/invite-links/{token}`, encoded separators included), URL queries and fragments, Bearer values, named token / password / authorization / cookie fields, JWTs.
- Kept: diagnostic file names and stack line numbers.
- Limits: 8 KB per report, 20 reports per minute per IP; Loki keeps them 14 days. This covers client-error telemetry, not every infrastructure log.

## Email verification

An account must verify its e-mail before it gets a session (V23: `email_verified_at`, the unique `email_verification_hash`, `email_verification_expires_at`, `email_verification_sent_at`). Accounts that existed before were left unverified with their sessions revoked; the dev seed is verified.

- **Enforced** by HTTP and STOMP access verification, by opening a refresh session and by every refresh.
- **Register** (`POST /auth/register`) answers 204 with no session. A duplicate e-mail also gets 204 without replacing anything; a username conflict answers the same 409 for known and unknown e-mails, checked before the e-mail lookup, and BCrypt runs on either outcome (no account enumeration).
- **Request a link** (`POST /auth/verification-email`, `{email}`): always 204 for valid input; only unverified, non-deleted users get mail, at most once per minute per user under a user row lock.
- **Verify** (`POST /auth/verify-email`, `{token, password}`): 204, or 400 for an invalid, expired or replayed token or a wrong password. Requiring the password stops mail scanners, forwarding and pre-registration from activating an account; no GET changes it.
- **Links:** 256 random bits, only the SHA-256 stored, 30 minutes, a resend replaces the previous link. Verifying consumes the token, increments `tokenVersion` and closes old STOMP sessions after the commit. A password reset does not verify the e-mail.
- **Mail:** sent after the commit through `EmailSender`; user data is HTML-escaped and the event's `toString` hides the credentials. Without a configured sender no link is delivered; a resend recovers a failed send.
- Account deletion clears the verification state; the export carries `account.emailVerifiedAt`, never the hashes.
- **Rollback:** never to a binary without verification; keep the additive schema and roll forward.

## Token identity and stale writes

- An access JWT is valid only when its positive, immutable `userId`, its e-mail and its `ver` match the same user row; tokens without `userId` are rejected. HTTP and STOMP share `AccessTokenVerifier`.
- `users.row_version` (V25, `@Version`) makes a stale write fail with 409 instead of overwriting a concurrent password, preference, deletion or trial change; the bulk trial update increments it too, and subscription creation reloads the creator after it.
- A logout between the Redis reads and the rotation of a refresh makes the rotation fail; a logout after a rotation ends the new token too.
- **Rollback:** only to a binary that keeps these checks; keep the additive schema.

## Antipatterns

Don't introduce these. Numbers are shared by the whole backend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 4 | Unauthorized WebSocket subscriptions / anonymous CONNECT | Authorize `SUBSCRIBE` to `/topic/pelada/{id}` for members only; reject CONNECT without a valid token | — |
| 12 | In-memory, never-evicted rate-limit map keyed by the proxy IP | A filter with an evicting cache (Caffeine) and the real client IP (`server.forward-headers-strategy=native`, nginx overwriting `X-Forwarded-For`), keyed by the route's logical name on the decoded path | — |
| 13 | Upload extension taken from the client filename; old image deleted before the transaction commits; replaced images never deleted | Extension from the validated content type; delete after commit (`TransactionSynchronization`); delete the previous file | — |
| 18 | Two CORS sources that disagree | One `ALLOWED_ORIGINS` list used by MVC, Security (`http.cors()`) and WebSocket | — |
