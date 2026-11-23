# Package layout and architecture

> Packages by domain, where a class goes and the layering rules. Part of the [backend guide](README.md).

## Rules

1. Controller → Service → Repository; a controller reads the principal, validates with `@Valid` and calls one service method.
2. DTOs only at the boundary (records, static `from`); controllers never return entities.
3. A new feature is a package under `domain/`; a package holds at most 12 classes and a domain's sub-packages never form a cycle (`DomainLayoutTest`).
4. Callers through `UserAuthenticationHelper`, relationships through `PeladaAccessHelper`, uploads through `FileUploadService`, aggregates through `AggregateRebuildService`.
5. A feature that notifies publishes a domain event; it never calls the delivery directly.

## Package layout

Base package `com.futspring.backend` (`S/`). `FutSpringApplication` is the main class (`@EnableScheduling`, `@EnableAsync`); `dev/DataInitializer` is the dev seed (`dev` profile only). Features live under `domain/`, one package per domain, each with its request/response records in `dto/`. Code used by most domains lives in `shared/`.

### `shared/`

| Package | Holds |
|---|---|
| `config/` | `SecurityConfig` (actuator + API filter chains, BCrypt), `CorsConfig` (the one `ALLOWED_ORIGINS` list), `JwtConfig`, `WebSocketConfig` (STOMP `/ws`), `TimeConfig` (the `Clock` bean, UTC), `TaskSchedulingConfig` |
| `exception/` | `AppException`, `ErrorResponse`, `GlobalExceptionHandler` ([error-handling.md](error-handling.md)) |
| `helper/` | `UserAuthenticationHelper` (caller from the e-mail principal), `PeladaAccessHelper` (member / admin / creator exists queries, `require*`) |
| `entity/` | `EntityIdentity`: id-based `equals` / `hashCode`, safe with Hibernate proxies |

### `domain/`

| Domain | Owns | Sub-packages |
|---|---|---|
| `auth` | register / login, JWT (`JwtService`, `JwtAuthFilter`, `AccessTokenVerifier`), STOMP authentication (`JwtChannelInterceptor`, `StompSessions`), rate limits (`AuthRateLimitFilter`), `LegalTerms`, `SecretTokens` | `session` (refresh sessions in Redis, the cookie), `passwordreset`, `verification` (e-mail verification) |
| `user` | profile, avatar, password change, account deletion (`AccountDeletionService`), data export (`AccountExportService`), `TokensRevokedEvent` | — |
| `pelada` | pelada CRUD, members, admins, stars, mensalistas, transfer, user search (`PeladaService`); membership events | `member` (stars, mensalista rows), `invitation` (invitations, share link, quotas; entities in `invitation/model`) |
| `daily` | sessions, status, detail (`DailyService`), teams sorting and editing (`DailyTeamManagementService`), the hourly scheduler, `DailyDTOMapper` | `attendance` (confirm / withdraw, window, capacity, bumps), `team`, `results` (results, league table, finalize, populate) |
| `stats` | ranking, awards, stats, attendance ranking (read side), `AggregateRebuildService` | `model` (aggregate entities and their grouped queries) |
| `finance` | the finance services, `FinanceTransactions`, `FinanceAccess`, `FinancePreservation` (the only entry point for other domains) | `collection`, `ledger`, `account` (entities by aggregate) — [finance.md](finance.md) |
| `subscription` | the subscription entity, `SubscriptionState`, `SubscriptionGuard`, `SubscriptionService`, `SubscriptionPlans`, the notice job | `billing` (payments, events), `checkout` (the creator's area, Pix BR Code) — [subscriptions.md](subscriptions.md) |
| `platform` | the platform admin panel (`PlatformAccess`, read and command services) | — |
| `notification` | `NotificationService` (event listeners), types and categories, `NotificationDelivery`, `NotificationContent`, scheduled and retention jobs | `inbox` (REST, STOMP push, DTO assembler), `preference` (preferences, unsubscribe tokens), `email` (dispatcher, pacer), `webpush` — [notifications.md](notifications.md) |
| `chat` | messages over STOMP and their paged history | — |
| `file` | `FileUploadService` (decode, re-encode, store, delete after commit), `ImageDecoder`, `FileController` / `FileReadService` (private reads) | — |
| `mail` | `EmailSender` (Resend, or the no-op without a key), `MailConfig` | — |
| `tour` | the guided tours each user has seen | — |
| `telemetry` | `POST /client-errors`: one redacted log line per report | — |

The entities and their tables are in [domain-model.md](domain-model.md); the routes in the `api-*.md` documents.

New code goes in the domain it belongs to (a new feature is a new package under `domain/`); cross-domain references are plain imports (entities, services and DTOs are public). Code needed by most domains goes in `shared/`. Response DTOs map themselves with a static `from(entity)`; entities are listed in [Domain model](domain-model.md#domain-model). When a service grows past ~300 lines or mixes responsibilities, split it by sub-domain the way the daily services are split (`DailyService` orchestrates, `DailyAttendanceService` / `DailyTeamManagementService` / `DailyResultsService` own one concern each).

**Where a class goes.** A new domain starts flat (controller, service, entity, repository at its root, DTOs in `dto/`). A package holds at most **12 classes directly** (`dto/` aside); whoever adds the 13th splits it in the same PR:

1. **By subject first:** a group of 3 or more classes about the same thing becomes a sub-package named after it, singular and lowercase (`invitation`, `attendance`, `webpush`), as long as the domain's sub-packages don't depend on each other in a cycle.
2. **By aggregate when the services are a mesh** (they call each other in a cycle, or separating them would need more than 10 members made `public`): the services stay together at the root and the entities leave with their repositories, enums and value types, grouped by aggregate (`finance/collection`, `finance/ledger`, `finance/account`), or in a single `model/` when there is only one (`stats/model`).

Placement: a class goes in the sub-package of its subject when only that subject (and other domains) uses it; at the domain root when two or more sub-packages use it or it is the domain's entry point (main entity, orchestrating service, guard). An entity and its repository share a package. An event sits next to the service that publishes it; an enum or value type next to the entity it describes. DTOs are always in `<domain>/dto/`. Sibling sub-packages never depend on each other in a cycle; the root may depend on them and they on it.

Package-private members stay package-private. Making one `public` to allow a split goes in its own commit before the moves, listed in the PR. `DomainLayoutTest` (ArchUnit) fails the build on a package with more than 12 classes or a cycle between a domain's sub-packages (`dto/` is left out of both).

## Architectural patterns

- **Controller → Service → Repository.** Controllers only read the principal, validate the body (`@Valid`) and call **one** service method. Authorization, business rules and mapping live in services.
- **DTOs only at the boundary.** Domain request/response DTOs and their nested DTOs are Java records. Controllers never return entities. Response DTOs expose a static `from(entity)` factory (`PeladaResponseDTO.from`, `RankingDTO.from`…); aggregates that need several sources are assembled in the service or a dedicated mapper (`DailyDTOMapper`).
- **Domain errors** are thrown as `new AppException(HttpStatus.X, "mensagem")` and turned into `ErrorResponse` by `GlobalExceptionHandler`.
- **Caller resolution.** The principal is the email (`@AuthenticationPrincipal String email` in controllers); services load the user with `UserAuthenticationHelper.getAuthenticatedUser(email)`. Don't repeat `userRepository.findByEmail(...).orElseThrow(...)`.
- **Relationship checks** go through `PeladaAccessHelper` (exists queries), never `pelada.getMembers().contains(...)`.
- **Uploads** always go through `FileUploadService` (decoded from the content, re-encoded without metadata, UUID names, extension of the re-encoded format; see [Uploads](security.md#uploads)). Replaced files are removed with `deleteImageAfterCommit`, so a rollback never loses the old image. Files are served with relationship authorization by `FileController` / `FileReadService` (`GET /api/v1/files/{filename}`, with a path-traversal guard).
- **Notifications.** Services publish domain events (`DiaristaBumpedEvent`, `DailyCreatedEvent`, `DailyCanceledEvent`, `AttendanceChangedByAdminEvent`, `TeamsSortedEvent`, `DailyFinalizedEvent` in `domain/daily`; `InvitationCreatedEvent`, `MemberJoinedEvent`, `MembersRemovedEvent`, `AdminsChangedEvent`, `MensalistasChangedEvent`, `PeladaTransferredEvent` in `domain/pelada`) carrying the entities they already loaded. `NotificationService` handles them with plain `@EventListener`s, so the rows are written in the publisher's transaction (a rollback leaves none), and registers `NotificationDelivery.deliver(ids)` for after the commit, which pushes them over STOMP (`NotificationPushService`, on Boot's default virtual-thread `@Async` executor: it reloads the rows with fetch joins, builds the DTOs through `NotificationDTOAssembler` and sends each recipient `NotificationPushDTO {notification, unreadCount}` on `/user/queue/notifications`; a failure is only logged, the row is stored and the client recounts on reconnect) then queues the e-mails (`NotificationEmailDispatcher`, see [Notification e-mails](notifications.md#notification-e-mails)) and finally sends the Web Push to the recipients' devices (`WebPushDispatcher`, see [Web Push](notifications.md#web-push)); each channel follows its own preference and fails on its own. A collapsed notification keeps its id, so the client replaces it (and its Web Push `tag` replaces the one shown on the device). Rules: the actor is never notified; `TEAMS_SORTED` / `SESSION_FINISHED` reuse the recipient's unread row for that session (its `createdAt` moves to now); `AWARD_WON` only on the first finalize, with no actor (the admin who finalized hears about their own award); admin/mensalista changes only notify members whose status changed; stars never notify. `SESSION_REMINDER` / `DIARISTA_WINDOW_OPEN` have no event: `ScheduledNotificationJob` picks their recipients with queries that skip whoever already has that type for the session (`NOT EXISTS`, the real guard; `uq_notifications_scheduled` is only the production backstop, H2 doesn't have it) and saves them through `NotificationService.notifyScheduled`, so the same after-commit delivery applies. A feature that should notify publishes an event and gets a type in `NotificationType` (and the V-migration CHECK).
- **Denormalized aggregates.** `Ranking`, `Stats` and `UserDailyStats` are rebuilt from match data on finalize, results edits on a finalized daily, daily delete and pelada delete, through `AggregateRebuildService.rebuild(pelada, players)`. Any feature that changes match data must call it.

## Antipatterns

Don't introduce these. Numbers are shared by the whole backend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 10 | Copy-pasted business logic | Extract a method/service | — |
| 11 | Business logic or several service calls in a controller | One service method; filters for cross-cutting concerns | `DailyController` finalize/populate call the mutating service, then `getDailyDetail` (kept so the response is read in its own transaction) |
