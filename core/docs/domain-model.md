# Domain model

> Entities and tables, the daily status machine and the attendance rules. Part of the [backend guide](README.md).

## Rules

1. Entities: `IDENTITY` ids, **`LAZY`** relations, `@Getter @Setter`, id-based `equals`/`hashCode` (`EntityIdentity`), `toString` with the id only.
2. Status-like fields are Java enums with `EnumType.STRING` and a CHECK constraint in the migration.
3. No JPA cascades: deletes are explicit in the services.
4. A daily changes status only through `DailyStatus.canTransitionTo`; confirmations run under the daily's row lock (`findByIdForUpdate`), with time from the injected `Clock`.
5. Avoid a digit before a capital in field names, or name the column.

## Domain model

All entities use IDENTITY ids and **LAZY** fetching, Lombok `@Getter @Setter` (no `@Data`), `@ToString` with the id only, and id-based `equals`/`hashCode` that is safe with Hibernate proxies (`EntityIdentity`). `Daily.status` is the `DailyStatus` enum (`EnumType.STRING`, same DB values as before); `User.position` is a string validated by `UpdateProfileRequest`. Since Boot 4 the physical naming strategy separates a digit from the next capital (`team1Score` → `team1_score`); columns created before with the old name carry an explicit `@Column` (`Match.team1score` / `team2score`), and new fields should avoid a digit before a capital or name the column.

| Entity | Table | Fields / relations |
|--------|-------|--------------------|
| `User` | `users` | `email` (unique; stored trimmed and in lower case, `User.normalizeEmail`, applied in the compact constructor of `RegisterRequestDTO`, `LoginRequestDTO` and `ForgotPasswordRequestDTO` and by the user search; also unique on `LOWER(email)` since V22), `username` (unique case-insensitively in `AuthService`/`UserService`, not in the DB: production has legacy duplicates, so check with `existsByUsernameIgnoreCase…`, never a single-result find), `password` (BCrypt), `image`, `backgroundImage`, `position`, `rowVersion` (`@Version`, `BIGINT`, default 0 since V25; protects every ORM update against stale state; bulk trial updates increment it too), `tokenVersion` (default 0, incremented by a password reset, a password change or the account deletion to revoke every JWT issued before), `termsVersion` / `termsAcceptedAt` (the terms of use and privacy policy version accepted at registration and when; null for accounts created before V16; kept by the account deletion as the record of the acceptance), `deletedAt` (`Instant`, null; set by the account deletion, `isDeleted()`; see [Account deletion](api-accounts.md#account-deletion)), `platformAdmin` (operator of the platform; turned on by SQL only, no route writes it), `trialUsedAt` (`Instant`, null until the account's one trial is taken by the first pelada it creates; see [Subscriptions](subscriptions.md#subscriptions-domainsubscription)), `createdAt` / `updatedAt` (`Instant`, Hibernate `@CreationTimestamp` / `@UpdateTimestamp`; null for accounts created before V16, and bulk statements such as `markTrialUsed` don't move `updatedAt`). The legacy `users.stars` column is no longer mapped (default 3 since V8) and will be dropped by a later migration |
| `Pelada` | `peladas` | `name`, `dayOfWeek` (a `java.time.DayOfWeek` name, `MONDAY`..`SUNDAY`, validated on create/update), `timeOfDay`, `duration`, `address`, `reference`, `image`, `autoCreateDailyEnabled`, `numberOfTeams` (2), `playersPerTeam` (5), `timeZone` (IANA region id, default `America/Sao_Paulo`, validated against `ZoneId.getAvailableZoneIds()`), `maxMensalistas` (0; `0 ≤ max ≤ numberOfTeams × playersPerTeam`, `capacity()`), `createdAt`; `creator` → User; `members` M:N (`pelada_members`); `admins` M:N (`pelada_admins`) |
| `Daily` | `dailies` | `dailyDate`, `dailyTime`, `status` (`DailyStatus`, default `SCHEDULED`), `isFinished`, `championImage`, `createdAt`; `pelada`. Attendance is the `DailyAttendance` entity (no collection on `Daily`) |
| `DailyAttendance` | `daily_confirmed_players` (PK `daily_id`+`user_id`) | `@EmbeddedId DailyAttendanceId (dailyId, userId)` with `@MapsId` `daily` and `user`; `confirmedAt` (`Instant`, `TIMESTAMPTZ`, set by the server clock). Read through `DailyAttendanceRepository` (confirmed users in confirmation order, grouped counts, the bump victim). Removed through the persistence context (`deleteAllOfDaily`), never a bulk delete: a managed row left in the transaction keeps Hibernate from deleting its daily |
| `DailyAttendanceBump` | `daily_attendance_bumps` | `daily`, `user` (the bumped diarista), `bumpedBy` (the mensalista), `bumpedAt`, `seenAt` (null until the diarista dismisses the alert). History of the session, shown to every member; deleted with the daily |
| `PeladaMensalista` | `pelada_mensalistas` (unique `pelada_id`+`user_id`) | `createdAt`; `pelada`, `user`. Having a row = mensalista; every other member is a diarista (creator and admins included). Deleted when the member is removed or the pelada is deleted |
| `Team` | `teams` | `name`, `color`; `daily`; `players` M:N (`team_players`) |
| `Match` | `matches` | `team1Score`, `team2Score`; `daily`, `team1`, `team2`, `winner` |
| `PlayerMatchStat` | `player_match_stats` | `goals`, `assists`; `match`, `user`. One row per player of the **two teams in that match** (`submitResults` and `populateFromMessage`), so `UserDailyStats.matchesPlayed` counts the matches a player actually played |
| `UserDailyStats` | `user_daily_stats` | `goals`, `assists`, `matchesPlayed`, `wins`, `wonSession`; `daily`, `user`. One row per player on the session's teams, written by finalize (and by `submitResults` on a FINISHED daily) |
| `LeagueTableEntry` | `league_table_entries` | `position`, `wins`, `draws`, `losses`, `goalsFor`, `goalsAgainst`, `points`; `daily`, `team` |
| `DailyAward` | `daily_awards` | `daily` (unique); `puskasWinners`, `wiltballWinners`, `artilheiroWinners`, `garcomWinners` (M:N `daily_award_*`) |
| `Ranking` | `rankings` (unique `pelada_id`+`user_id`) | `goals`, `assists`, `matchesPlayed`, `wins`; `pelada`, `user` |
| `Stats` | `stats` | `goals`, `assists`, `matchesPlayed`, `wins`, `sessionsPlayed`, `matchWins`, `puskasDates` (`stats_puskas_dates`); `user` (1:1) |
| `PasswordResetToken` | `password_reset_tokens` | `tokenHash` (SHA-256 hex of the token sent by e-mail, unique), `expiresAt` (30 min), `usedAt`, `createdAt`; `user`. Usable while `usedAt` is null and `expiresAt` is in the future |
| `PeladaPlayerStars` | `pelada_player_stars` (unique `pelada_id`+`user_id`) | `stars` (1–5), `updatedAt`; `pelada`, `user`. A member's stars in one pelada, set by its admins; a member without a row has `DEFAULT_STARS` (3). Read in one query per pelada (`findStarsByPelada`); deleted when the member is removed or the pelada is deleted. Used by team sorting, the member/team/player DTOs and the profile average |
| `Message` | `messages` | `content` (≤ 500), `sentAt`; `pelada`, `sender` |
| `Notification` | `notifications` | `type` (`NotificationType`, its category and emailable flag live in the enum), `award` (`NotificationAward`, only on `AWARD_WON`), `validUntil` (`Instant`, only on `SUBSCRIPTION_ENDING` / `SUBSCRIPTION_PAST_DUE`: the subscription's `valid_until` the notice was about, a snapshot; V21), `createdAt`, `readAt` (null = unread); `recipient`, `pelada`, `daily`, `actor` (null for system events), nullable `invitationId` (for invitation links; legacy `ADDED_TO_PELADA` remains readable). Only references are stored; the text is rendered by the client. Deleted with its daily (`DailyService.deleteDailyData`) or pelada (`PeladaService.deletePelada`) |
| `UserTour` | `user_tours` (PK `user_id`+`tour`) | `@EmbeddedId UserTourId (userId, tour)` with `@MapsId` `user`, `completedAt`. A row = the user finished, closed or skipped that guided tour of the web app (`TourKey`), so it doesn't start by itself again, on any device. Created in V13; deleted with the user (`ON DELETE CASCADE`) |
| `NotificationPreference` | `notification_preferences` (PK `user_id`+`category`) | `@EmbeddedId NotificationPreferenceId (userId, category)` with `@MapsId` `user`, `emailEnabled`, `pushEnabled` (`Boolean`, null = `NotificationCategory.defaultPush`, on in every category; a value equal to the default is stored as null). A row exists only when a choice differs from the defaults (e-mail: `ATTENDANCE`, `MEMBERSHIP`, `BILLING` on; `SESSION`, `AWARDS` off), so changing a default reaches everyone who never touched it. Created in V10, `push_enabled` in V18; deleted with the user (`ON DELETE CASCADE`) |
| `PushSubscription` | `push_subscriptions` (unique `endpoint`) | `user`, `endpoint` (≤ 1024, the push service URL), `p256dh` (the browser's P-256 key, base64url), `auth` (16-byte secret, base64url), `createdAt`, `lastSuccessAt` (last 201 from the push service). One row per browser/device; at most 10 per user. Created in V18; deleted explicitly on password reset/change and account deletion (`ON DELETE CASCADE` as a safety net) |
| `PeladaSubscription` | `pelada_subscriptions` (unique `pelada_id`) | `pelada` (1:1), `kind` (`SubscriptionKind`: `TRIAL`, `PENDING`, `PAID`, `EXEMPT`, `CANCELED`), `validUntil` (`Instant`, required for `TRIAL`/`PAID` by a CHECK), `plan` (`SubscriptionPlan` `MONTHLY`/`ANNUAL`, null), `founder`, `pixClaimedAt`, `claimedPlan`, `note`, `createdAt`, `updatedAt`. Follows the pelada on a transfer; deleted with it (`SubscriptionService.deletePeladaData`) |
| `SubscriptionPayment` | `subscription_payments` | `peladaId` (plain id, FK `ON DELETE SET NULL`: the payment outlives the pelada), `peladaName`, `payer` + `payerName` (snapshots), `amount` (`NUMERIC(10,2)`, > 0), `paidOn` (`LocalDate`), `plan`, `periodStart` / `periodEnd`, `recordedBy`, `note`, `voidedAt` + `voidReason` (a voided payment leaves the totals), `createdAt` |
| `SubscriptionEvent` | `subscription_events` | `peladaId`, `actor` (null = the system), `type` (`SubscriptionEventType`), `kindBefore` / `kindAfter`, `validUntilBefore` / `validUntilAfter`, `note`, `createdAt`. History of the subscription; deleted with the pelada |

There are no JPA cascades: deletes are explicit in the services (`DailyService.deleteDailyData`, `PeladaService.deletePelada`), notifications included.

**Daily status machine** (`DailyStatus.canTransitionTo`): `SCHEDULED → CONFIRMED | CANCELED`, `CONFIRMED → IN_COURSE | CANCELED`. Starting (`→ IN_COURSE`) also needs startable teams (`TeamsReadiness.requireStartable`, under the attendance row lock): at least 2 teams, none empty, every team player still confirmed; otherwise 409 with the first problem ("Monte os times antes de iniciar a sessão", "O Time 3 está vazio", "Ana não está mais confirmada e está no Time 2"). Short teams and confirmed players without a team don't block. `FINISHED` is reached only through finalize; populate can also move a daily to `IN_COURSE`. Attendance and team changes are blocked while the status is in `DailyStatus.LOCKED` (`IN_COURSE`, `FINISHED`, `CANCELED`).

Invitation entities (all in `domain/pelada/invitation/model`): `PeladaInvitation` → `pelada_invitations` (`pelada`, `recipient`, `sender`, status and creation/expiry/response/cancellation instants); `PeladaInviteLink` → `pelada_invite_links` (`pelada`, `creator`, creation/expiry/revocation); `PeladaLinkBlock` → `pelada_link_blocks` (`pelada`, `user`, creation, unique pair). Pelada deletion explicitly removes notifications before all three new record types; database cascades are a safety net.

### Attendance: mensalistas and diaristas

| Who confirms | Mensalista | Diarista |
|---|---|---|
| The player themselves (`POST /dailies/{id}/confirm`) | any time while the session isn't locked | only from **24 h before** the start (403 "Diaristas podem confirmar a partir de sáb, 31/10 às 20:00") |
| Admin (single or "Confirmar todos") | any time | any time |

- **Time:** `AttendanceWindow.startsAt` = `dailyDate` + `dailyTime` in the pelada's `timeZone`; the window opens at `startsAt − 24 h` (real hours, so it can open at a different wall-clock time across a DST change). "Now" is `Instant.now(clock)`; the confirm request has no body and nothing time-related is read from the client. `confirmedAt` is set by the server.
- **Capacity** (`numberOfTeams × playersPerTeam`): a diarista confirming into a full session gets 409 "Sessão lotada". A **mensalista** confirming into a full session removes the diarista with the latest `confirmedAt` (ties: highest user id; the type is read at that moment, so someone made mensalista after confirming is never a victim), records a `DailyAttendanceBump` and publishes `DiaristaBumpedEvent`; with no diarista confirmed, the mensalista joins over capacity. A diarista confirmed by an admin can be bumped too.
- **Admins** skip the window and the capacity (they can push a session over the limit) and never bump anyone.
- **Lock:** every confirmation loads the daily with `DailyRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`, a database row lock held until commit), so the count and the victim choice never race and it works across API instances; other sessions don't wait. `setMensalista` and a `maxMensalistas` change lock the pelada row the same way.
- **Withdrawing leaves the team:** a self withdrawal, an admin removal and a bump take the player out of their team in the same transaction (`DailyTeamManagementService.removeFromTeams`); the team stays, even empty, and the mensalista who bumped comes in without a team. The admin fills the spot in the teams editor or re-sorts.
- No waiting list: nobody is promoted when someone withdraws; a bumped diarista may confirm again under the normal rules.
- **Bump e-mail:** the bump is a `DIARISTA_BUMPED` notification (Attendance, e-mail on by default), e-mailed like every other one (see [Notification e-mails](notifications.md#notification-e-mails)), with a link to `/daily/{id}`. A Resend failure is only logged; the in-app alert (`myBump` / `nextDaily.bump` until `bumps/seen`) still shows.
- **Mensalista limit:** marking past `maxMensalistas` → 409 "Limite de mensalistas atingido (8/8)"; lowering the limit below the current count → 409 "Desmarque 2 mensalistas antes de reduzir o limite" (the system never picks who loses the status); a limit above the capacity → 409 "O limite de mensalistas não pode passar das vagas (10)".
- Read paths stay at a constant number of queries: mensalista ids per pelada in one query, the caller's mensalista peladas in one query, unseen bumps for `/peladas/my` in one query.

The calendar day for `nextDaily` and the six-month attendance-ranking period comes from the injected `Clock` in each pelada's `timeZone`. `PeladaSummaryAssembler.toSummaries` captures one instant for the response and groups pelada ids by local date before the next-session query (at most three date groups worldwide), preserving bounded query count without loading all future sessions. Same-date ties still use the lowest session id. The client's overview/session selection and relative labels use the same pelada calendar. No DTO or schema changes.

**Positions** (`UpdateProfileRequest`, `@Pattern`): `GOALKEEPER`, `DEFENDER`, `MIDFIELDER`, `FORWARD`, `GOLEIRO`, `ZAGUEIRO`, `MEIO`, `ATACANTE`; an empty string clears it. The web app offers the Portuguese four.

**Awards:** Puskás (best goal), "Wiltball" (shown as "Bola Murcha" in the UI), Artilheiro (top scorer), Garçom (top assists).

New status-like fields must be Java enums stored with `@Enumerated(EnumType.STRING)`.

## Antipatterns

Don't introduce these. Numbers are shared by the whole backend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 8 | Lombok `@Data` on entities (equals/hashCode over every field and lazy collections; `toString` triggers lazy loads) | `@Getter @Setter`, id-based equals/hashCode, no collection in `toString` | — |
| 9 | Magic strings for status/position duplicated across services | Java enums (`DailyStatus`, `Position`) with `EnumType.STRING`, one shared set of locked statuses | `User.position` (a validated string) |
| 14 | Free-text fields that code later parses (`DayOfWeek.valueOf`) | Validate on input (enum or `@Pattern`); isolate failures per item in jobs | — |
| 15 | Deleting a parent that still has children (FK violation → 500) | Delete or cascade children explicitly in the service, or forbid with 409 | — |
