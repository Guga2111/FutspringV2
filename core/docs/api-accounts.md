# API: auth, users and account

> Auth, files, users, account deletion, tours and client errors. Part of the [backend guide](README.md).

All routes are under `/api/v1`. "Auth" is the relationship checked (see the matrix); the invitation-link preview and the e-mail preference links are also public (see below).

## Auth (`AuthController`)
| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | `/auth/register` | `RegisterRequestDTO {username, email, password, termsVersion}` (`email` normalized, see `User`; `password` ≥ 8 and ≤ 72 bytes in UTF-8 (`@BcryptLength`: BCrypt takes 72 bytes and Spring Security's encoder throws above that, in `encode` and `matches`); `termsVersion` `@NotBlank`, max 20) | 204, no token/cookie; creates an unverified account and sends verification mail. Existing emails also return 204 without replacing credentials; 409 "Os termos foram atualizados…" when `termsVersion` ≠ `LegalTerms.CURRENT_VERSION` (checked first); records `termsVersion` + `termsAcceptedAt` (server clock) |
| POST | `/auth/login` | `LoginRequestDTO` (`email` normalized; `password` ≤ 72 bytes) | 200 `AuthResponseDTO` + refresh cookie for verified users (none with Redis down); correct credentials for an unverified user return 403 and request a verification email (cooldown applies); 401 "E-mail ou senha inválidos" for an unknown e-mail or a wrong password alike: an unknown e-mail is still compared against `AuthService.DUMMY_HASH` (BCrypt cost 10), so the timing doesn't reveal accounts; 429 after 10/min per IP |
| POST | `/auth/forgot-password` | `ForgotPasswordRequestDTO {email}` (normalized) | 204 always (also for unknown e-mails); sends the reset e-mail when the account exists |
| POST | `/auth/reset-password` | `ResetPasswordRequestDTO {token, newPassword ≥ 8, ≤ 72 bytes}` | 204; 400 when the token is unknown, used or expired |
| POST | `/auth/refresh` | — (the `futspring_refresh` cookie; `Origin` in `ALLOWED_ORIGINS`) | 200 `AuthResponseDTO` + a new refresh cookie; 401 clearing the cookie when the session ended; 403 without an allowed `Origin`; 503 with Redis down; 429 after 30/min per IP. See [Tokens](security.md#tokens-sdomainauthjwtservicejava) |
| POST | `/auth/verification-email` | `{email}` (ForgotPasswordRequestDTO) | 204 for valid input, account existence hidden; one email/user/minute |
| POST | `/auth/verify-email` | `VerifyEmailRequestDTO {token,password}` | 204 on verification; 400 invalid/expired/replayed link or wrong password; 30-minute single-use link |
| POST | `/auth/logout` | — (the cookie) | 204, clears the cookie; idempotent |

## Files (`FileController`)
| Method | Path | Auth | Response |
|--------|------|------|----------|
| GET | `/files/{filename}` | self/shared pelada for player images; member for pelada/champion images | bytes, `Cache-Control: no-store`, nosniff; 401 anonymous, 404 unknown/unrelated/orphan |

## Client errors (`ClientErrorController`, `/client-errors`)
Uncaught errors of the web app (`client/src/lib/errorReporting.ts`). **Public**: in `permitAll`, the Authorization header is ignored (`JwtAuthFilter.shouldNotFilter`), the caller is never read.

| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | `/client-errors` | `ClientErrorRequestDTO {message @NotBlank ≤ 500, stack ≤ 4000, path @NotBlank ≤ 200, release ≤ 40, userAgent ≤ 300, source: WINDOW \| PROMISE \| BOUNDARY \| CSP}` (all but `message` and `path` optional) | 204; nothing stored: one WARN line (see [Observability](observability.md#observability)), `path` cut at its first `?` / `#`. 400 validation; 413 over 8 KB (`ClientErrorBodyLimitFilter` reads at most 8 KB + 1 byte, so a body without `Content-Length` is bounded too); 429 after 20/min per IP |

## Tours (`TourController`, `/tours`)
The caller's guided tours of the web app (today the finance page, one tour per role). Only what was seen is stored.

| Method | Path | Body | Response |
|--------|------|------|----------|
| GET | `/tours` | — | `TourStateDTO {seen: [TourKey]}` (`FINANCE_MANAGER`, `FINANCE_MEMBER`), in enum order |
| POST | `/tours/{tour}/seen` | — | 204; idempotent (the first `completedAt` is kept); unknown key → 400 |

## Users (`UserController`, `/users`)
| Method | Path | Body | Auth | Response |
|--------|------|------|------|----------|
| POST | `/users/me/password` | `ChangePasswordRequestDTO {currentPassword @NotBlank ≤ 72 bytes, newPassword @NotBlank ≥ 8, ≤ 72 bytes}` | self | `AuthResponseDTO {token, user}` + a new refresh cookie: new BCrypt hash, `tokenVersion++` (ends every refresh session) and a new session of the new version (the other sessions get 401 on their next request; open STOMP connections are closed after the commit); every Web Push subscription of the caller is deleted (this device subscribes again on open). Wrong current password → 400 `{errors: {currentPassword: "Senha atual incorreta"}}` (`AppException.field`); rate limited |
| GET | `/users/me/export` | — | self | `AccountExportDTO` as a download (`Content-Disposition: attachment; filename="futspring-meus-dados-yyyy-MM-dd.json"`, `Cache-Control: no-store`): LGPD access and portability. `formatVersion` (1), `exportedAt`, `account` (no password, token version or platform flag), `peladas` (each membership with `isCreator`, `isAdmin`, `isMensalista`, `stars`), `stats` / `matchHistory` (the self views of `/stats` and `/stats/matches`), `attendance` (every confirmation with `confirmedAt`), `bumps` (where they lost the spot), `messages` (only the ones they sent), `finance` (`FinanceReadService.accountExport`: their charges in published lists, also of peladas they left, with components, due, received; the notices they sent, `receiptSent` but no file), `subscriptions` (of the peladas they created) and `subscriptionPayments` (where they are the payer, voided ones included), `notifications`, `notificationPreferences`, `toursSeen`, `pushDevices` (dates only, no endpoint or keys). Nothing about other people (their e-mails, charges, messages, the bumper), no Pix of the receivers, no audit. One or two queries per block whatever the data (`AccountExportTest` compares 1 and 5 peladas with `QueryCounter.statements`); rate limited |
| GET | `/users/me/deletion-blockers` | — | self | `List<DeletionBlockerDTO {type: CREATOR \| SOLE_FINANCE_MANAGER, peladaId, peladaName}>`: peladas the caller created, then peladas where they are the only responsável financeiro (`FinancePreservation.soleManagedPeladas`); two queries |
| POST | `/users/me/delete` | `DeleteAccountRequestDTO {password @NotBlank ≤ 72 bytes}` (POST: a DELETE with a body is poorly supported) | self | 204; wrong password → 400 on `password`; a blocker → 409 with the first one ("Transfira ou exclua a Pelada do Fut antes de excluir a conta"); rate limited. See [Account deletion](#account-deletion) |
| GET | `/users/search?q=` | — | any | `List<PublicUserDTO {id, username, image, position, isDeleted}>` (max 10, by username); the username containing `q` (case-insensitive; `%`, `_` and `\` are literal, escaped by `UserService`) or the e-mail **equal** to `q` (any case; a fragment never matches, so the search can't map e-mails to accounts), never a deleted account; `q` needs ≥ 3 characters (400) |
| GET | `/users/{id}` | — | any | `ProfileDTO` (`email` and `isPlatformAdmin` only for self; `stars` = the rounded average of the user's stars in every pelada they belong to, members without a row counting 3, omitted when they belong to none) |
| GET | `/users/{id}/peladas` | — | any | `List<PeladaResponseDTO>`: peladas the caller and `{id}` both belong to (`isAdmin`/`isFinanceManager`/`nextDaily.isConfirmed` refer to the caller) |
| PUT | `/users/{id}` | `UpdateProfileRequest {username 3–30, position}` (stars are set per pelada by admins) | self | `ProfileDTO` |
| POST | `/users/{id}/image`, `/users/{id}/background-image` | multipart `file` | self | `ProfileDTO` |
| GET | `/users/{id}/stats` | — | self or shares a pelada | `StatsDTO` |
| GET | `/users/{id}/stats/timeline?from&to` | — | self or shares a pelada | `UserStatsTimelineDTO` (other viewers: shared peladas only; `from` after `to` → 400) |
| GET | `/users/{id}/stats/matches` | — | self or shares a pelada | `UserMatchHistoryDTO` (other viewers: shared peladas only) |

When an endpoint or DTO changes, update the matching `api-*.md` table, the frontend `src/api/*` + `src/types/*`, and [the frontend data layer](../../client/docs/data-layer.md) in the same change.

## Account deletion

`AccountDeletionService.deleteAccount` (`domain/user`) **anonymizes**, it never deletes the `users` row: past matches, awards, rankings, chat and other people's finance point at it. One transaction: it checks the password, locks every pelada the user belongs to in id order (`PeladaRepository.lockIdsByMemberId`, a native `FOR UPDATE` returning ids only), recomputes the blockers under those locks (409) and then each domain does its part with bulk statements, so the reads don't grow with the number of peladas (`AccountSettingsTest` compares 1 and 5 peladas with `QueryCounter`):

| Target | Action |
|---|---|
| `users` | Row kept: `email = "deleted-{id}@invalid"` (the e-mail can register again), `password` = BCrypt of a random value, `username = "Ex-jogador"` (`AccountDeletionService.DELETED_USERNAME`), `image`, `backgroundImage`, `position` = null, `tokenVersion++`, `deletedAt = now` |
| Peladas (`PeladaService.removeAccountFromPeladas`) | Leaves every pelada as member and admin, with their stars and mensalista rows, and the attendance and teams of `SCHEDULED`/`CONFIRMED` sessions (`DailyService.removeAccountFromUpcomingSessions`; the teams stay); no `REMOVED_FROM_PELADA` notification, no `PeladaLinkBlock` |
| Finance (`FinancePreservation.forgetAccount`) | Stops being a responsável (a `MANAGERS_REVOKED` audit entry and a revision per pelada); `FinancePerson.name = "Ex-jogador"`. Charges, receipts, notices, cash and audit stay |
| Invitations (`PeladaInvitationService.forgetAccount`) | Pending received → `CANCELED` (`EXPIRED` when past due); links they created → revoked; their link blocks deleted |
| Deleted | Notifications received, `notification_preferences`, `password_reset_tokens`, `user_tours`, `push_subscriptions`, `daily_attendance_bumps` where they were bumped |
| Kept | `player_match_stats`, `user_daily_stats`, past `team_players`, `daily_award_*`, `rankings`, `stats`, `messages`, notifications of others where they are the `actor` |
| Files | Avatar and background: `deleteImageAfterCommit` |

Reads: `/users/{id}`, `/stats*` and `/peladas` of a deleted account → 404, search skips it, and `PublicUserDTO`, `DailyDetailDTO.PlayerDTO` and `MessageDTO.SenderDTO` carry `isDeleted`. The ranking and the attendance ranking only list current members, so the deleted user drops out of them while everyone else's numbers stay. No e-mail goes to `@invalid`: they belong to no pelada any more and `ScheduledNotificationJob` only picks members.
