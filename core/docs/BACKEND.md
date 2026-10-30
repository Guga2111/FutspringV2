# Backend Documentation — Futspring API

Spring Boot REST API in `core/`. Base package: `com.futspring.backend` (`src/main/java/com/futspring/backend`, written `S/` below).

Futspring manages amateur soccer groups ("peladas"): members and admins, match-day sessions ("dailies") with attendance, team sorting, match results, league table, awards, a pelada-wide ranking, per-player stats and a live chat.

## Stack

- Java 25, Spring Boot 4.1.2 (Spring Framework 7, Spring Security 7, Hibernate 7.4), Maven (`./mvnw`). Boot 4 is modular: each technology has its own starter (`spring-boot-starter-webmvc`, `-flyway`, `-restclient`…) and its own test starter (`-webmvc-test`, `-data-jpa-test`, `-security-test`, `-actuator-test`, `spring-boot-resttestclient`). Lombok runs through `annotationProcessorPaths` (JDK 23+ no longer picks processors up from the classpath)
- Spring Web MVC, Spring Data JPA (Hibernate), Spring Security, Bean Validation
- Jackson 3 (`tools.jackson.*`; annotations stay in `com.fasterxml.jackson.annotation`); inject `JsonMapper`. Jackson 2 is on the classpath only as `jjwt-jackson`'s runtime dependency
- Spring WebSocket — STOMP over SockJS, in-memory simple broker (chat)
- PostgreSQL 17 (runtime: Supabase in production, `postgres:17-alpine` in dev), H2 in PostgreSQL mode (tests)
- JJWT 0.13 (HS256), Bucket4j 8.21 (`bucket4j_jdk17-core`) + Caffeine (login/register rate limit, in memory), Lombok
- Tests: JUnit Jupiter 6, Mockito 5 (inline mock maker loaded as a Surefire `-javaagent`, JEP 451), AssertJ, spring-security-test, Testcontainers 2 (`testcontainers-postgresql`, managed by Boot)
- Flyway 12 (`spring-boot-starter-flyway`) + `flyway-database-postgresql` (schema migrations, see [Database schema & migrations](#database-schema--migrations))
- Spring Boot Actuator + Micrometer Prometheus registry, on an internal management port (see [Observability](#observability))
- springdoc-openapi 3 (OpenAPI document + Swagger UI), served only when `API_DOCS_ENABLED=true` (dev)
- Not present: Redis, caching, MapStruct/ModelMapper

## Commands

Run from `core/`:

```bash
./mvnw spring-boot:run                           # needs DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET
./mvnw test                                      # all tests (H2, src/test/resources/application.properties)
./mvnw test -Dtest=PeladaServiceTest             # one test class
./mvnw clean package -DskipTests                 # build jar
./mvnw test -Dtest=ContractSnapshotTest -Dcontract.update=true   # rewrite the JSON contract snapshots (see Testing)
docker compose -f docker-compose.dev.yml up --build   # API + postgres:17-alpine (dev seed); Swagger UI on http://localhost:8080/swagger-ui.html
docker compose -f docker-compose.dev.yml down -v      # drop the dev database (needed once: a Postgres 15 volume can't be opened by 17)
../ops/loadtest/run.sh pr1                       # local load test of the image (see Observability)
```

CI (`.github/workflows/ci-backend.yml`) runs `./mvnw -B verify` (every test, including `FlywayMigrationTest` and `MigrationDataTest`) on pushes and PRs to `main`/`dev` that touch `core/`. Run `./mvnw test` before finishing any backend change.

### CI/CD

- `.github/workflows/deploy.yml` runs on every push to `main` (and manually): it calls both CI workflows, writes `.env` from the GitHub secrets, runs `scripts/deploy.sh`, then smoke-tests `APP_URL` (200) and `<API_URL>/readyz` (200 through nginx, so a wrong `API_URL` prefix fails too).
- `scripts/deploy.sh` (tracked, no secrets) builds the frontend with bun and an amd64 Docker image, rsyncs `client/dist` to `/var/www/futspring`, copies the image, `core/docker-compose.prod.yml` and `.env` to `~/projects/futspring` on the VPS, backs up the database with `pg_dump` (when `PG_DUMP_URL` is set, last 10 kept in `backups/`), creates the shared `observability` network (idempotent), `chown`s `/opt/futspring-uploads` to uid 10001 (the image's user), starts the stack with `docker compose` (API on `127.0.0.1:${API_PORT:-8081}`, `mem_limit: 1g`, on its own network and `observability`) and waits up to 2 min for the container's `healthy` state (the image's `HEALTHCHECK` is readiness on the management port, so Flyway migrated and `validate` passed); otherwise it prints the logs and fails. It refuses a `.env` containing `DDL_AUTO`.
- `.github/workflows/deploy-observability.yml` deploys the shared Prometheus + Grafana stack (`ops/observability/`, see its README) on pushes to `main` that touch it, independently from the API.
- Locally: put the production values in `.env` at the repo root (gitignored, keys in `core/.env.example`) and run `VPS_IP=… VITE_API_URL=… ./scripts/deploy.sh`.

| GitHub | Name | Notes |
|--------|------|-------|
| secret | `VPS_SSH_KEY`, `VPS_SSH_KNOWN_HOSTS` | deploy key; `ssh-keyscan -H <ip>` (falls back to keyscan when empty) |
| secret | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` | required |
| secret | `RESEND_API_KEY` | optional for now; without it the API sends no e-mails (the deploy warns) |
| secret | `PG_DUMP_URL` | optional; libpq URL on the Supabase session pooler (5432) for the pre-migration backup |
| secret | `GRAFANA_ADMIN_PASSWORD` | required by `deploy-observability.yml` |
| variable | `VPS_IP`, `APP_URL` | required |
| variable | `GRAFANA_ROOT_URL` | `deploy-observability.yml`; defaults to `https://grafana.luisgosampaio.com` |
| variable | `VPS_USER` (`root`), `API_URL` (baked in as `VITE_API_URL` and written to `.env` as the API's `API_URL`, defaults to `APP_URL`; production is `https://futspring.luisgosampaio.com/api` because nginx's `location /api/` strips the prefix before proxying to `:8081`), `ALLOWED_ORIGINS` (defaults to `APP_URL`), `API_PORT` (`8081`), `JWT_EXPIRATION_MS`, `MAIL_FROM` (defaults to `Futspring <no-reply@futspring.luisgosampaio.com>`) | optional |

## Configuration & environment

There is a single `src/main/resources/application.properties`; there are no `application-<profile>` files.

| Variable | Purpose | Default |
|----------|---------|---------|
| `DB_URL` | JDBC URL | `jdbc:postgresql://futspring-postgres-dev:5432/futspringdb` |
| `DB_USERNAME`, `DB_PASSWORD` | DB credentials | — (required) |
| `JWT_SECRET` | HS256 key; **must be ≥ 32 bytes** (`Keys.hmacShaKeyFor`) | — (required) |
| `JWT_EXPIRATION_MS` | access token TTL | `604800000` (7 days) |
| `DDL_AUTO` | `spring.jpa.hibernate.ddl-auto`; leave unset (the schema is owned by Flyway) | `validate` |
| `ALLOWED_ORIGINS` | allowed browser origins, comma-separated; used by HTTP CORS (Spring Security `http.cors`) and the STOMP endpoint (`app.cors.allowed-origins`, `CorsConfig`) | `http://localhost:5173` |
| `APP_URL` | frontend base URL (`app.frontend-url`), used to build links sent by e-mail | `http://localhost:5173` |
| `API_URL` | public base URL of this API (`app.api-url`), for the one-click `List-Unsubscribe` header the mail provider calls; production `https://futspring.luisgosampaio.com/api` | `http://localhost:8080` |
| `RESEND_API_KEY` | Resend API key (`app.mail.resend-api-key`); empty means no e-mail is sent | — |
| `MAIL_FROM` | sender (`app.mail.from`), must belong to the domain verified in Resend (`futspring.luisgosampaio.com`) | `Futspring <no-reply@futspring.luisgosampaio.com>` |
| `SPRING_PROFILES_ACTIVE` | `dev` runs the seed (`docker-compose.dev.yml`), `prod` in production | — |
| `API_DOCS_ENABLED` | serves the OpenAPI document (`/v3/api-docs`) and Swagger UI (`/swagger-ui.html`) and makes them public (`springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled`); `true` in `docker-compose.dev.yml`, unset in production (the web app is the only client) | `false` |

Locally, `docker-compose.dev.yml` passes `RESEND_API_KEY`, `MAIL_FROM`, `APP_URL` and `API_URL` from `core/.env` (gitignored, next to the compose file). Compose only uses `.env` to fill `${…}` in the file, so a new variable reaches the container only when it is listed under `environment`.

- `spring.threads.virtual.enabled=true`: Tomcat handles each HTTP request on a virtual thread. Boot's `applicationTaskExecutor` (`SimpleAsyncTaskExecutor`) runs `@Async` on virtual threads without a concurrency limit. `spring.task.execution.mode=force` is required because STOMP declares `Executor` beans that otherwise suppress this auto-configuration; do not expose the notification e-mail executor as a bean.
- `TaskSchedulingConfig` imports Boot 4.1's `DefaultTaskSchedulerConfiguration` **after** `TaskSchedulingAutoConfiguration`: STOMP's `messageBrokerTaskScheduler` otherwise suppresses the default scheduler. `@Scheduled` resolves the named `taskScheduler` (`SimpleAsyncTaskScheduler`) and runs cron/one-shot jobs on virtual threads; the STOMP/SockJS scheduler remains separate. Fixed-delay jobs run on the scheduler thread; current production jobs use cron.
- Hikari: `spring.datasource.hikari.maximum-pool-size=10`, `spring.datasource.hikari.connection-timeout=5000` (ms), repeated in test config. Database access stays bounded by ten connections; waiting longer than five seconds for a connection fails the request with the existing 500 response.
- Uploads go to `app.uploads.dir=/app/uploads` (container path; override it when running outside Docker). Multipart limit is 10 MB, but `FileUploadService` enforces 5 MB.
- `server.forward-headers-strategy=framework`: behind nginx, `getRemoteAddr()` is the client IP (rate limiting). The API only listens on `127.0.0.1`.
- Hibernate: `show-sql=false`, `batch_size=50`, ordered inserts/updates; the dialect is detected from the connection (not set). `spring.jpa.open-in-view` is not set, so it is **on** (default).
- Management (Actuator): `management.server.port=9091`, endpoints `health`, `info`, `metrics`, `prometheus`, `threaddump`, health details always shown (only reachable on 9091), probes on, `add-additional-paths` serves `/livez` and `/readyz` on the main port too, every metric tagged `application=futspring`, percentile histograms for `http.server.requests`.
- `DataInitializer` (`@Profile("dev")`) seeds an empty database with 20 users (password `senha123`; admin `leal@futspring.com`), the pelada "Pelada do Fut", 7 weekly finished dailies and 1 scheduled daily for today. Each finished daily gets teams (4 teams of 5, or 3 teams / 15 confirmed players in two of the weeks so histories differ), 6 matches with random results from a fixed seed (`new Random(2026)`), and is closed through `DailyResultsService.submitResults` + `finalizeDaily`, so match stats, league table, `UserDailyStats`, `Ranking`, `Stats` and awards follow the real rules.
  It only runs with the `dev` profile, never in tests or production.
- Test config (`src/test/resources/application.properties`, which replaces the main file in tests, so any property tests need is repeated there): H2 PostgreSQL mode, `create-drop`, Flyway disabled, fixed test JWT secret, 1 h TTL, uploads in a temp dir, the same management settings on a random port.
- **Secrets:** all credentials come from the environment (`.env`, `.env.example` documents the keys). Never write a password, DB URL with credentials, or JWT secret into a tracked file, script, compose file or doc.

## Package layout

Features live under `domain/`, one package per domain (`domain/daily`, `domain/pelada`…). Each domain holds its controller, services, entities and repositories at its root, with request/response classes in `dto/`; the two big domains (`daily`, `stats`) also split `entity/` and `repository/`. Code used by every domain lives in `shared/`; the dev seed in `dev/`.

```
com.futspring.backend
├── FutSpringApplication        main class, @EnableScheduling, @EnableAsync (e-mails)
├── shared/
│   ├── config/                 SecurityConfig (actuator + API filter chains, BCrypt), CorsConfig (ALLOWED_ORIGINS + CorsConfigurationSource, used by
│   │                           SecurityConfig and WebSocketConfig), JwtConfig (@Value secret + TTL), WebSocketConfig (STOMP /ws, SockJS,
│   │                           broker /topic, app prefix /app, JwtChannelInterceptor), TimeConfig (Clock bean, UTC; Clock.fixed in tests), TaskSchedulingConfig (ordered import of Boot's default scheduler alongside STOMP)
│   ├── exception/              AppException (RuntimeException + HttpStatus), ErrorResponse, GlobalExceptionHandler
│   ├── helper/                 UserAuthenticationHelper (getAuthenticatedUser(email) → User or 404),
│   │                           PeladaAccessHelper (isMember/isAdmin/isCreator, requireMember/requireAdmin, exists queries)
│   └── entity/EntityIdentity   id-based equals/hashCode for every entity, safe with Hibernate proxies
├── domain/                   one package per feature
│   ├── auth/                       AuthController, AuthService (register / login, issues JWT), JwtService (generate / validate / extract),
│   │   │                           JwtAuthFilter (Bearer → email principal), AccessTokenVerifier (signature + user's current tokenVersion),
│   │   │                           AuthRateLimitFilter (10/min per IP on every POST /auth route,
│   │   │                           Bucket4j + Caffeine), JwtChannelInterceptor (authenticates STOMP CONNECT/SUBSCRIBE/SEND),
│   │   │                           PasswordResetService (forgot / reset password), PasswordResetToken + repository, ResetTokens
│   │   │                           (random token + SHA-256), PasswordResetEmailListener (sends the e-mail after commit, @Async),
│   │   │                           PasswordResetEmail (fills resources/email/password-reset.html), PasswordResetTokenCleanup (daily purge)
│   │   └── dto/                    LoginRequestDTO, RegisterRequestDTO, AuthResponseDTO, UserResponseDTO,
│   │                               ForgotPasswordRequestDTO, ResetPasswordRequestDTO
│   ├── user/                       UserController, UserService (profile get/update, avatar, background image), User, UserRepository
│   │   └── dto/                    ProfileDTO, PublicUserDTO, UpdateProfileRequest
│   ├── pelada/                     PeladaController, PeladaService (pelada CRUD, members, admins, member stars, mensalistas, user search, image, creator transfer; each member action works on a list of members and the single routes call it with one id), Pelada, PeladaRepository,
│   │                               PeladaPlayerStars + PeladaPlayerStarsRepository (stars per member, profile average),
│   │                               PeladaMensalista + PeladaMensalistaRepository (members marked as mensalistas)
│   │   └── dto/                    Create/UpdatePeladaRequestDTO, DayOfWeekPattern, PeladaResponseDTO, PeladaDetailResponseDTO, PeladaMemberDTO, UpdatePlayerStarsRequestDTO,
│   │                               SetMensalistaRequestDTO, Bulk{SetAdmin,SetMensalista,SetStars,RemovePlayers}RequestDTO, TransferCreatorRequestDTO…
│   ├── daily/                      DailyController and the daily services:
│   │   │                           DailyService (create, list, status transitions, delete, detail aggregation)
│   │   │                           DailyAttendanceService (confirm / unconfirm, self and by admin; diarista window, capacity, mensalista bump,
│   │   │                           row lock; bumps seen), AttendanceWindow (session start / diarista window in the pelada's zone),
│   │   │                           DiaristaBumpedEvent (the bump's notification and e-mail go through domain/notification)
│   │   │                           DailyTeamManagementService (sort teams with LPT balanced by the stars in the pelada, swap, rename, color)
│   │   │                           DailyResultsService (results, live league table, finalize, champion image, populate, clearResults)
│   │   │                           DailySchedulerService (hourly cron that auto-creates the next daily, in the pelada's zone), DailyDTOMapper (Player/Team/MatchDTO),
│   │                           DailyListItemAssembler (DailyListItemDTO with counts and the caller's attendance, grouped queries)
│   │   ├── entity/                 Daily, DailyStatus, DailyAttendance (+ DailyAttendanceId), DailyAttendanceBump, Team, Match, PlayerMatchStat, LeagueTableEntry
│   │   ├── repository/             Daily, DailyAttendance, DailyAttendanceBump, Team, Match, PlayerMatchStat, LeagueTableEntry repositories
│   │   └── dto/                    DailyDetailDTO, DailyListItemDTO, AttendanceBumpDTO, MatchResultDTO, Finalize/Populate/Swap/Update* request DTOs
│   ├── stats/                      RankingService, AwardsService, StatsService, AttendanceService (read-side aggregates; attendance ranking of the last 6 months),
│   │   │                           AggregateRebuildService (rebuilds Ranking per pelada and global Stats for a set of players, batch queries)
│   │   ├── entity/                 Ranking, Stats, UserDailyStats, DailyAward
│   │   ├── repository/             Ranking, Stats, UserDailyStats, DailyAward repositories (grouped/native aggregate queries)
│   │   └── dto/                    RankingDTO, StatsDTO, PeladaAwardsDTO, PeladaAttendanceDTO, PlayerPelada{Stats,History}DTO, UserMatchHistoryDTO, UserStatsTimelineDTO
│   ├── chat/                       ChatController (STOMP + @MessageExceptionHandler), ChatService (save message, paged history), Message, MessageRepository
│   │   └── dto/                    MessageDTO, SendMessageRequest
│   ├── notification/               NotificationService (synchronous @EventListener for every domain event: recipients, actor dropped,
│   │   │                           collapse of repeatable types, saves the rows and registers the after-commit delivery),
│   │   │                           NotificationInboxService (list / count / read / read-all of the caller), NotificationController,
│   │   │                           Notification + NotificationRepository, NotificationType (category + emailable), NotificationCategory,
│   │   │                           NotificationAward, NotificationDelivery (after-commit hook: the push, then the e-mail),
│   │   │                           NotificationPushService (@Async STOMP push to /user/queue/notifications),
│   │   │                           NotificationDTOAssembler (NotificationDTOs of one or many recipients, one team query),
│   │   │                           NotificationPreference (+ Id) + repository + NotificationPreferenceService (e-mail per category, only
│   │   │                           what differs from the default is stored; unsubscribe / resubscribe by token), UnsubscribeTokens (HMAC),
│   │   │                           NotificationEmailDispatcher (own single-thread executor, bounded queue), EmailPacer (Bucket4j, 2/s),
│   │   │                           NotificationEmailContent (exhaustive switch: subject, title, body, CTA per type),
│   │   │                           NotificationEmail (fills resources/email/notification.html),
│   │   │                           ScheduledNotificationJob (every 15 min: SESSION_REMINDER / DIARISTA_WINDOW_OPEN once the diarista
│   │   │                           window opens), NotificationRetentionJob (04:00: deletes read notifications > 90 days, any > 180 days)
│   │   └── dto/                    NotificationDTO, NotificationPushDTO, UnreadCountDTO, NotificationPreferenceDTO, UnsubscribeRequestDTO
│   ├── mail/                       EmailSender (interface, takes InlineImage CID attachments and extra headers; enabled() is false
│   │                               for the no-op), ResendEmailSender (Resend HTTP API through RestClient, 5 s / 10 s timeouts),
│   │                               NoopEmailSender (no API key: logs recipient + subject only), MailConfig (picks the sender,
│   │                               exposes app.frontend-url and app.api-url for links)
│   ├── file/                       FileController (public GET /files/{filename}), FileUploadService (stores/deletes images on local disk,
│   │                               UUID + extension from the content type, delete after commit)
└── dev/DataInitializer         dev seed (CommandLineRunner, dev profile only)
```

New code goes in the domain it belongs to (a new feature is a new package under `domain/`); cross-domain references are plain imports (entities, services and DTOs are public). Code needed by most domains goes in `shared/`. A new domain starts flat (controller, service, entity, repository at its root, DTOs in `dto/`) and splits `entity/` / `repository/` only when it grows like `daily` did. Response DTOs map themselves with a static `from(entity)`; entities are listed in [Domain model](#domain-model). When a service grows past ~300 lines or mixes responsibilities, split it by sub-domain the way the daily services are split (`DailyService` orchestrates, `DailyAttendanceService` / `DailyTeamManagementService` / `DailyResultsService` own one concern each).

## Architectural patterns

- **Controller → Service → Repository.** Controllers only read the principal, validate the body (`@Valid`) and call **one** service method. Authorization, business rules and mapping live in services.
- **DTOs only at the boundary.** Controllers never return entities. Response DTOs expose a static `from(entity)` factory (`PeladaResponseDTO.from`, `RankingDTO.from`…); aggregates that need several sources are assembled in the service or a dedicated mapper (`DailyDTOMapper`).
- **Domain errors** are thrown as `new AppException(HttpStatus.X, "mensagem")` and turned into `ErrorResponse` by `GlobalExceptionHandler`.
- **Caller resolution.** The principal is the email (`@AuthenticationPrincipal String email` in controllers); services load the user with `UserAuthenticationHelper.getAuthenticatedUser(email)`. Don't repeat `userRepository.findByEmail(...).orElseThrow(...)`.
- **Relationship checks** go through `PeladaAccessHelper` (exists queries), never `pelada.getMembers().contains(...)`.
- **Uploads** always go through `FileUploadService` (size and content-type checks, UUID names, extension from the content type). Replaced files are removed with `deleteImageAfterCommit`, so a rollback never loses the old image. Files are served publicly by `FileController` (`GET /api/v1/files/{filename}`, with a path-traversal guard).
- **Notifications.** Services publish domain events (`DiaristaBumpedEvent`, `DailyCreatedEvent`, `DailyCanceledEvent`, `AttendanceChangedByAdminEvent`, `TeamsSortedEvent`, `DailyFinalizedEvent` in `domain/daily`; `MembersAddedEvent`, `MembersRemovedEvent`, `AdminsChangedEvent`, `MensalistasChangedEvent`, `PeladaTransferredEvent` in `domain/pelada`) carrying the entities they already loaded. `NotificationService` handles them with plain `@EventListener`s, so the rows are written in the publisher's transaction (a rollback leaves none), and registers `NotificationDelivery.deliver(ids)` for after the commit, which pushes them over STOMP (`NotificationPushService`, on Boot's default virtual-thread `@Async` executor: it reloads the rows with fetch joins, builds the DTOs through `NotificationDTOAssembler` and sends each recipient `NotificationPushDTO {notification, unreadCount}` on `/user/queue/notifications`; a failure is only logged, the row is stored and the client recounts on reconnect) and then queues the e-mails (`NotificationEmailDispatcher`, see [Notification e-mails](#notification-e-mails)). A collapsed notification keeps its id, so the client replaces it. Rules: the actor is never notified; `TEAMS_SORTED` / `SESSION_FINISHED` reuse the recipient's unread row for that session (its `createdAt` moves to now); `AWARD_WON` only on the first finalize, with no actor (the admin who finalized hears about their own award); admin/mensalista changes only notify members whose status changed; stars never notify. `SESSION_REMINDER` / `DIARISTA_WINDOW_OPEN` have no event: `ScheduledNotificationJob` picks their recipients with queries that skip whoever already has that type for the session (`NOT EXISTS`, the real guard; `uq_notifications_scheduled` is only the production backstop, H2 doesn't have it) and saves them through `NotificationService.notifyScheduled`, so the same after-commit delivery applies. A feature that should notify publishes an event and gets a type in `NotificationType` (and the V-migration CHECK).
- **Denormalized aggregates.** `Ranking`, `Stats` and `UserDailyStats` are rebuilt from match data on finalize, results edits on a finalized daily, daily delete and pelada delete, through `AggregateRebuildService.rebuild(pelada, players)`. Any feature that changes match data must call it.

## Domain model

All entities use IDENTITY ids and **LAZY** fetching, Lombok `@Getter @Setter` (no `@Data`), `@ToString` with the id only, and id-based `equals`/`hashCode` that is safe with Hibernate proxies (`EntityIdentity`). `Daily.status` is the `DailyStatus` enum (`EnumType.STRING`, same DB values as before); `User.position` is a string validated by `UpdateProfileRequest`. Since Boot 4 the physical naming strategy separates a digit from the next capital (`team1Score` → `team1_score`); columns created before with the old name carry an explicit `@Column` (`Match.team1score` / `team2score`), and new fields should avoid a digit before a capital or name the column.

| Entity | Table | Fields / relations |
|--------|-------|--------------------|
| `User` | `users` | `email` (unique), `username` (unique case-insensitively in `AuthService`/`UserService`, not in the DB: production has legacy duplicates, so check with `existsByUsernameIgnoreCase…`, never a single-result find), `password` (BCrypt), `image`, `backgroundImage`, `position`, `tokenVersion` (default 0, incremented by a password reset to revoke every JWT issued before). The legacy `users.stars` column is no longer mapped (default 3 since V8) and will be dropped by a later migration |
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
| `Notification` | `notifications` | `type` (`NotificationType`, its category and emailable flag live in the enum), `award` (`NotificationAward`, only on `AWARD_WON`), `createdAt`, `readAt` (null = unread); `recipient`, `pelada`, `daily`, `actor` (null for system events). Only references are stored; the text is rendered by the client. Deleted with its daily (`DailyService.deleteDailyData`) or pelada (`PeladaService.deletePelada`) |
| `NotificationPreference` | `notification_preferences` (PK `user_id`+`category`) | `@EmbeddedId NotificationPreferenceId (userId, category)` with `@MapsId` `user`, `emailEnabled`. A row exists only when the user's choice differs from `NotificationCategory.defaultEmail` (`ATTENDANCE`, `MEMBERSHIP` on; `SESSION`, `AWARDS` off), so changing a default reaches everyone who never touched it. Created in V10; deleted with the user (`ON DELETE CASCADE`) |

There are no JPA cascades: deletes are explicit in the services (`DailyService.deleteDailyData`, `PeladaService.deletePelada`), notifications included.

**Daily status machine** (`DailyStatus.canTransitionTo`): `SCHEDULED → CONFIRMED | CANCELED`, `CONFIRMED → IN_COURSE | CANCELED`. `FINISHED` is reached only through finalize; populate can also move a daily to `IN_COURSE`. Attendance and team changes are blocked while the status is in `DailyStatus.LOCKED` (`IN_COURSE`, `FINISHED`, `CANCELED`).

### Attendance: mensalistas and diaristas

| Who confirms | Mensalista | Diarista |
|---|---|---|
| The player themselves (`POST /dailies/{id}/confirm`) | any time while the session isn't locked | only from **24 h before** the start (403 "Diaristas podem confirmar a partir de sáb, 31/10 às 20:00") |
| Admin (single or "Confirmar todos") | any time | any time |

- **Time:** `AttendanceWindow.startsAt` = `dailyDate` + `dailyTime` in the pelada's `timeZone`; the window opens at `startsAt − 24 h` (real hours, so it can open at a different wall-clock time across a DST change). "Now" is `Instant.now(clock)`; the confirm request has no body and nothing time-related is read from the client. `confirmedAt` is set by the server.
- **Capacity** (`numberOfTeams × playersPerTeam`): a diarista confirming into a full session gets 409 "Sessão lotada". A **mensalista** confirming into a full session removes the diarista with the latest `confirmedAt` (ties: highest user id; the type is read at that moment, so someone made mensalista after confirming is never a victim), records a `DailyAttendanceBump` and publishes `DiaristaBumpedEvent`; with no diarista confirmed, the mensalista joins over capacity. A diarista confirmed by an admin can be bumped too.
- **Admins** skip the window and the capacity (they can push a session over the limit) and never bump anyone.
- **Lock:** every confirmation loads the daily with `DailyRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`, a database row lock held until commit), so the count and the victim choice never race and it works across API instances; other sessions don't wait. `setMensalista` and a `maxMensalistas` change lock the pelada row the same way.
- No waiting list: nobody is promoted when someone withdraws; a bumped diarista may confirm again under the normal rules.
- **Bump e-mail:** the bump is a `DIARISTA_BUMPED` notification (Attendance, e-mail on by default), e-mailed like every other one (see [Notification e-mails](#notification-e-mails)), with a link to `/daily/{id}`. A Resend failure is only logged; the in-app alert (`myBump` / `nextDaily.bump` until `bumps/seen`) still shows.
- **Mensalista limit:** marking past `maxMensalistas` → 409 "Limite de mensalistas atingido (8/8)"; lowering the limit below the current count → 409 "Desmarque 2 mensalistas antes de reduzir o limite" (the system never picks who loses the status); a limit above the capacity → 409 "O limite de mensalistas não pode passar das vagas (10)".
- Read paths stay at a constant number of queries: mensalista ids per pelada in one query, the caller's mensalista peladas in one query, unseen bumps for `/peladas/my` in one query.

**Positions** (`UpdateProfileRequest`, `@Pattern`): `GOALKEEPER`, `DEFENDER`, `MIDFIELDER`, `FORWARD`, `GOLEIRO`, `ZAGUEIRO`, `MEIO`, `ATACANTE`; an empty string clears it. The web app offers the Portuguese four.

**Awards:** Puskás (best goal), "Wiltball" (shown as "Bola Murcha" in the UI), Artilheiro (top scorer), Garçom (top assists).

New status-like fields must be Java enums stored with `@Enumerated(EnumType.STRING)`.

## Database schema & migrations

### How the schema is managed

- **Flyway 12** (`spring-boot-starter-flyway` + `flyway-database-postgresql`, versions managed by Spring Boot; since Flyway 10 each database lives in its own module) runs `src/main/resources/db/migration/V*.sql` at startup, before JPA.
- Hibernate runs with `ddl-auto=${DDL_AUTO:validate}`: it never changes the schema, only checks that the tables match the entities, and the app fails to start if they don't. `DDL_AUTO` is not set by `deploy.sh` or the compose files (the deploy fails if `.env` sets it); keep the default.
- `spring.flyway.baseline-on-migrate=true` + `baseline-version=1`: a database that already has tables but no `flyway_schema_history` (production, an old dev volume) is marked as V1 without running it, and only later versions run. An empty database runs V1 and builds the whole schema.
- `V1__baseline.sql` is the schema Hibernate generated from the entities on 2026-10-03 (PostgreSQL 15, `pg_dump --schema-only`), not a dump of production. Production's tables were built by `ddl-auto=update` from the same entities. Checked against a copy of production on 2026-10-04: same columns and constraints, except two leftover nullable columns `daily_awards.puskas_winner_id` / `wiltball_winner_id` (all null, FKs to `users`) that no entity maps; V4 drops them.
- Production (Supabase), dev (`docker-compose.dev.yml`) and the migration tests all run PostgreSQL 17, which Flyway 12 supports (no "newer than this version" warning).
- Tests: H2 `create-drop` with Flyway disabled for the unit/integration tests; `FlywayMigrationTest` (Testcontainers, `postgres:17-alpine` like production, skipped without Docker) runs every migration on an empty Postgres and validates it against the entities; `MigrationDataTest` runs the data-changing migrations (V4, V5, V8, V9) against production-shaped rows.

| Version | What it does |
|---------|--------------|
| V1 | Baseline schema |
| V2 | Deletes `player_match_stats` rows of players on neither team of the match (keeps rows with goals/assists) and rebuilds `matches_played` in `user_daily_stats`, `rankings`, `stats` (destructive: back up first) |
| V3 | Indexes every FK and lookup column (`pelada_id`, `daily_id`, `user_id`, `team_id`, `match_id`, award join tables, `messages(pelada_id, sent_at)`); non-destructive |
| V4 | Drops the production-only legacy columns `daily_awards.puskas_winner_id` / `wiltball_winner_id`; aborts if any row has a value (destructive: back up first) |
| V5 | Converts Portuguese `peladas.day_of_week` values (`Segunda`, `Sabado`…) to `MONDAY`..`SUNDAY`; unknown values are left as they are |
| V6 | Creates `password_reset_tokens` (FK to `users` with `ON DELETE CASCADE`, index on `user_id`); non-destructive |
| V7 | Adds `users.token_version` (`INTEGER NOT NULL DEFAULT 0`); existing sessions stay valid; non-destructive |
| V8 | Creates `pelada_player_stars` (FKs `ON DELETE CASCADE`, unique `(pelada_id, user_id)`, index on `user_id`), copies each member's `users.stars` into every pelada they belong to, and sets `DEFAULT 3` on `users.stars` (no longer mapped); non-destructive. Pending: a later migration drops `users.stars` (destructive: back up first) |
| V9 | Adds `peladas.time_zone` (`DEFAULT 'America/Sao_Paulo'`) and `max_mensalistas` (`DEFAULT 0`, `CHECK ≥ 0`); creates `pelada_mensalistas` (unique `(pelada_id, user_id)`) and `daily_attendance_bumps` (indexes on `daily_id`, `bumped_by_id` and unseen `user_id`); adds `daily_confirmed_players.confirmed_at` (`TIMESTAMPTZ`, backfilled with `dailies.created_at`, then `NOT NULL DEFAULT now()`). FKs `ON DELETE CASCADE` as a safety net only; non-destructive |
| V10 | Creates `notifications` (CHECK on `type` and `award`; FKs `ON DELETE CASCADE`, `actor_id` `ON DELETE SET NULL`, as a safety net only; indexes `(recipient_id, created_at DESC)`, partial `(recipient_id) WHERE read_at IS NULL`, one per FK, and the unique partial `(recipient_id, type, daily_id)` for the scheduled types, checked by `FlywayMigrationTest`) and `notification_preferences`; non-destructive |

### Migration rules

1. **Location and naming:** `src/main/resources/db/migration/V<n>__<snake_case_description>.sql` (`V7__add_pelada_timezone.sql`). Numbers are sequential; check `main` before picking one.
2. **Every entity change ships with its migration in the same PR.** The entity and the SQL must agree, or `validate` fails at startup.
3. **Never edit, rename or delete a migration that has run anywhere.** Fix mistakes with a new migration.
4. **Expand / contract for breaking changes:** add the new nullable column → backfill (in the migration or a one-off job) → set `NOT NULL` / add the constraint → remove the old column in a **later** release. Never rename a column in one step while the old app version may still be running.
5. **Constraints and indexes are explicit.** Index every FK and lookup column (`pelada_id`, `daily_id`, `user_id`, `team_id`, `match_id`) and keep uniqueness in the DB (`rankings(pelada_id, user_id)`, `stats(user_id)`, `daily_awards(daily_id)`).
6. **Enums:** stored as `EnumType.STRING`; adding a value means updating the CHECK constraint in the migration.
7. **Aggregates:** a change that affects `Ranking`, `Stats` or `UserDailyStats` must include a backfill or a documented rebuild step.
8. **Destructive migrations** (drop, retype, delete rows) require a database backup (Supabase) before deploy and a note in the PR.
9. Mention new migrations in the PR description.

## Security

### Filter chain (`S/shared/config/SecurityConfig.java`)

- Two chains. `actuatorFilterChain` (`@Order(1)`, `EndpointRequest.toAnyEndpoint()`) permits every actuator endpoint: they are only served on the management port 9091, which Docker never publishes (Boot applies the security filter to that port too, so without this chain Prometheus would get 401). `filterChain` (`@Order(2)`) is everything else.
- CSRF disabled, stateless sessions, JSON 401 entry point.
- Public: `/api/v1/auth/**`, `/api/v1/files/**`, `/ws/**` (the STOMP layer authenticates itself, see WebSocket), `GET /livez` / `/readyz` (probes, `https://futspring.luisgosampaio.com/api/readyz` through nginx) and `POST /api/v1/notifications/unsubscribe` / `/resubscribe` (`SecurityConfig.EMAIL_LINK_PATHS`, authorized by the HMAC token in the request). With `API_DOCS_ENABLED=true` (dev only), `GET /v3/api-docs/**`, `/swagger-ui/**` and `/swagger-ui.html` too; otherwise they aren't served and fall under the authenticated rule. Everything else requires a valid token.
- `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`. It accepts a Bearer token only through `AccessTokenVerifier`: valid signature, not expired, the user still exists and the `ver` claim equals `users.token_version` (one indexed query per authenticated request). Otherwise it returns 401 on **any** path, including the public ones, except `EMAIL_LINK_PATHS`, where it ignores the header (`shouldNotFilter`): a visitor whose stored session expired must still be able to use the e-mail's link, and those routes never read the caller.
- No roles: authorities are always empty, there is no `@PreAuthorize`/`@EnableMethodSecurity`. **All authorization is done in services** (see [Ownership](#ownership--authorization)).
- Passwords: BCrypt (strength 10).

### Tokens (`S/domain/auth/JwtService.java`)

- HS256 JWT, claims `sub` = email, `userId`, `email`, `ver` (the user's `tokenVersion`; tokens without it count as 0), `iat`, `exp`; TTL 7 days by default.
- Returned in the body (`AuthResponseDTO {token, user}`); the frontend keeps it in `localStorage`.
- No refresh token. Revocation is per user: incrementing `User.tokenVersion` (done by a password reset) invalidates every token issued before; a deleted user's tokens stop working too. There is no per-session logout on the server.

### CORS

- One list, `ALLOWED_ORIGINS` (comma-separated, property `app.cors.allowed-origins`), read by `CorsConfig`. Its `CorsConfigurationSource` is applied by Spring Security (`http.cors`), and `WebSocketConfig` uses the same origins for `/ws`.
- Production sets it to `APP_URL` (`deploy.yml`); the dev default is `http://localhost:5173`.

### Rate limiting

`AuthRateLimitFilter` (in the security chain): 10 requests per minute per client IP on each of `POST /api/v1/auth/login`, `/register`, `/forgot-password`, `/reset-password` and the e-mail link routes `/api/v1/notifications/unsubscribe` and `/resubscribe`, answering 429 with the usual error body. Buckets live in a Caffeine cache (max 100k entries, evicted 10 min after the last access). The IP is `getRemoteAddr()`, which reflects `X-Forwarded-For` because of `server.forward-headers-strategy=framework`. In memory: one API instance only. New rate limits go into a filter like this one.

### WebSocket (`S/domain/auth/JwtChannelInterceptor.java`)

- `CONNECT` needs `Authorization: Bearer <token>` in the STOMP headers, checked by `AccessTokenVerifier` like HTTP; otherwise the frame is rejected. A chat connection opened before a revocation stays open until it disconnects (SEND/SUBSCRIBE only check the session's principal).
- `SUBSCRIBE` is allowed only to `/topic/pelada/{id}` when the user is a member of that pelada (`existsByIdAndMembers_Email`), and to the user's own `/user/queue/errors` and `/user/queue/notifications` (`/user/...` resolves to the session's principal, so nobody can subscribe to another user's queue). Any other destination is rejected.
- `SEND` needs an authenticated session and a destination under `/app/`; a frame sent straight to a broker prefix (`/topic`, `/queue`, `/user`) is rejected, so every message goes through `ChatController` → `ChatService`.
- Clients send to `/app/pelada/{peladaId}/send` (`SendMessageRequest {content}`); the server broadcasts `MessageDTO` to `/topic/pelada/{peladaId}`. Errors (`AppException` from `ChatService`) go back to the sender on `/user/queue/errors` as `{status, message, timestamp}`.
- Notifications are pushed to `/user/queue/notifications` (see [Notifications](#notifications-notificationcontroller-notifications)). The web app opens one connection per logged-in user and shares it between the chat and the notifications.

### Password reset

- `POST /auth/forgot-password` always answers 204 and does the same work whether or not the e-mail has an account (no user enumeration). For an existing user it invalidates the pending tokens, stores the SHA-256 of a new 256-bit token (30 min) and publishes `PasswordResetRequestedEvent`; at most one e-mail per user per minute.
- The e-mail is `src/main/resources/email/password-reset.html` (table layout + inline styles + Outlook conditionals, adapted from a Stripo template; placeholders `{{appUrl}}`, `{{link}}`, `{{ttlMinutes}}`, `{{username}}`, the username HTML-escaped). The logo (`resources/email/futspring-logo.png`, 160 px) travels inside the e-mail as a Resend inline attachment (`InlineImage`, `content_id` → `<img src="cid:futspring-logo">`), so it shows locally too and doesn't depend on the web app being deployed.
- `PasswordResetEmailListener` sends the e-mail with Resend **after the commit** and on another thread (`@Async @TransactionalEventListener`), with the link `${app.frontend-url}/reset-password?token=…`. A Resend failure is only logged (Spring's async handler); the user asks again.
- `POST /auth/reset-password` finds the token by hash with a row lock (unused, not expired), sets the new BCrypt password and marks every pending token of the user as used. Any invalid token answers 400 "Link inválido ou expirado. Peça um novo.".
- The reset increments `User.tokenVersion`, so every session opened before (other browsers, a stolen token) gets 401 on its next request and the frontend sends it to `/auth`.
- `PasswordResetTokenCleanup` deletes tokens expired or used more than a day ago, daily at 04:00.

### Notification e-mails

- **When:** the type is emailable (`NotificationType.emailable`), the recipient's category is on (their `notification_preferences` row, else `NotificationCategory.defaultEmail`) and the sender is real (`EmailSender.enabled()`; the no-op only logs). The preferences of every recipient of a batch are read in one query.
- **Dispatch:** `NotificationDelivery` calls `NotificationEmailDispatcher.dispatch(ids)` after the commit; it only queues a task on its **own** single-platform-thread `ThreadPoolTaskExecutor` (core = max = 1, queue of 500), unchanged by virtual threads. The executor is created by the dispatcher, not exposed as a bean: an `Executor` bean would replace Spring Boot's default `@Async` executor. The task builds the e-mails in a short read-only transaction (`findAllByIdInWithRefs` + preferences) and sends them **outside** it, one by one, through `EmailPacer` (Bucket4j: one send every 500 ms, no burst, so never more than Resend's ~2 requests/s; 30 e-mails ≈ 15 s). A full queue, a failed build or a failed send is only logged; no retry. The Resend batch endpoint isn't used because it doesn't take attachments (the logo is `cid:`). Metrics: `futspring.email.queue.size`, `futspring.email.sent{result=ok|failed}`, `futspring.email.dropped` (full queue).
- **Text and template:** `NotificationEmailContent.of(notification)` is an exhaustive `switch` over `NotificationType` (a new type doesn't compile without its subject, title, body and CTA); the dates come from `AttendanceWindow` in the pelada's zone. `NotificationEmail` fills `resources/email/notification.html` (same structure and inline logo as the reset e-mail) in one pass, HTML-escaping every value, so a pelada or user name can never fill another placeholder.
- **Unsubscribe:** the footer says which category is on and links to "Desativar" (`${app.frontend-url}/notifications/unsubscribe?token=…`, a page that POSTs the token) and "Preferências" (`/settings/notifications`). The token (`UnsubscribeTokens`) is `base64url("userId:CATEGORY")` + `.` + HMAC-SHA256, keyed by an HMAC of `JWT_SECRET` with the context `"notification-unsubscribe"`; not stored, no expiry, it only turns that category of that user off or on. Every e-mail also carries `List-Unsubscribe: <${app.api-url}/api/v1/notifications/unsubscribe?token=…>` and `List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 8058), so Gmail / Yahoo can unsubscribe with a POST straight to the API.
- **Quota:** production is on Resend's free plan (100 e-mails/day, 3,000/month, shared with the password reset). That is enough at today's scale (it takes roughly 30 peladas of 40 players to matter); if it gets tight, the plan upgrade or the daily digest (phase 2) are the next steps. `SESSION_REMINDER` (Attendance, on by default) adds about one e-mail per unconfirmed mensalista per session; `DIARISTA_WINDOW_OPEN` is Session (off by default).
- The password reset e-mail stays separate: transactional, not a notification, no unsubscribe.

### Uploads

`FileUploadService` checks size (5 MB) and the content type (`image/jpeg`, `image/png`, `image/webp`); the stored name is a UUID plus the extension of that content type. New files are removed if the transaction rolls back, and replaced files (avatar, background, pelada image, champion photo) are deleted after the commit.

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
| Create pelada | any authenticated user (becomes creator, member and admin) |
| Read pelada detail, dailies, daily detail, ranking, attendance ranking, awards, member stats/history, chat history, send chat, subscribe to the chat topic | member (member stats/history: the target user must also be a member, else 404) |
| Update pelada (teams and players per team included), upload/remove pelada image, add/remove player, set/unset admin | admin (the creator can't be removed or demoted; remove/set admin need the target to be a member, else 404). Remove and set admin also work on a list of members: one request, one transaction, any invalid target fails the whole batch |
| Set a member's stars in the pelada | admin; any member, the creator and the caller included (target must be a member, else 404); one member or a list (atomic) |
| Mark/unmark a member as mensalista | admin; any member, the creator and the caller included (target must be a member, else 404; 409 past `maxMensalistas`); one member or a list (atomic: a batch that doesn't fit changes nobody) |
| Delete pelada (cascades dailies, results, teams, attendance, messages, rankings; rebuilds global Stats) | creator |
| Transfer the pelada (`PUT /peladas/{id}/creator`) | creator; the target must be an admin of the pelada; the old creator stays admin |
| Create daily, change status, delete daily, sort/swap teams, submit results, finalize, populate, champion image | admin of the daily's pelada |
| Confirm attendance (self) | member; diaristas only inside their window and while there are spots (see [Attendance](#attendance-mensalistas-and-diaristas)) |
| Dismiss the bump alert (`bumps/seen`) | member; only the caller's own bumps |
| Unconfirm (self) | must be confirmed |
| Confirm/unconfirm another player, confirm every member | admin, target must be a member; no window, no capacity, never bumps |
| Rename team | member **and** player on that team (team must belong to the daily) |
| Change team color | member and (team player or admin) |
| Update profile, avatar, background | self |
| View a profile (`/users/{id}`) | any authenticated user; `email` only for self |
| User stats, timeline, match history (`/users/{id}/stats…`) | self, or a user who shares a pelada (403 otherwise); non-self viewers only get sessions of the peladas they share |
| Peladas in common (`/users/{id}/peladas`) | any; returns only peladas both users belong to |
| Search users | any; public fields only (`PublicUserDTO`), query ≥ 3 characters |
| List / count / read notifications, e-mail preferences (`/notifications/**`) | self only: every route reads the caller from the token and takes no `userId`; someone else's notification id → 404 |
| Unsubscribe / resubscribe from an e-mail (`POST /notifications/unsubscribe`, `/resubscribe`) | public: the HMAC token names the user and category; an invalid token → 400 |

### Rules for new code

1. Resolve the caller from the token, never from the body or a path param. Ignore any `userId`/owner field the client sends for "who am I".
2. Every `{id}` route checks the relationship before doing anything else.
3. **Scope child ids to the parent in the path.** A `matchId`, `teamId` or `userId` in the body/path must be checked to belong to the `dailyId`/`peladaId` of the route (e.g. `team.getDaily().getId().equals(dailyId)`, as `updateTeamName` does). Otherwise an admin of one pelada can touch another pelada's data.
4. Use `PeladaAccessHelper` (exists queries) instead of loading `members`/`admins` collections just to call `contains`.
5. Don't expose email or other PII outside the pelada context; public user views return id, username, image, position.
6. 403 for "exists but you can't", 404 for "doesn't exist". When even revealing existence matters, answer 404 for both.
7. Each new `{id}` route gets tests for: not a member (403), member but not admin (403 where admin is required), and a child id from another pelada/daily.

### Known gaps (fix when touching these areas)

| Gap | Where |
|-----|-------|
| No unique constraint on `user_daily_stats(daily_id, user_id)` / `player_match_stats(match_id, user_id)` (the code never writes duplicates, but the database doesn't enforce it). Check production for duplicates before adding them | `V1__baseline.sql` |
| `nextDaily`'s "from today" (and the attendance ranking's period) still use the server's zone, not the pelada's `timeZone` | `PeladaService.toSummaries`, `AttendanceService` |
| The JWT is valid for 7 days and can only be revoked for all of a user's sessions at once (`tokenVersion`); an open chat connection survives the revocation until it reconnects | `JwtAuthFilter`, `JwtService` |

## Error handling (`S/shared/exception/GlobalExceptionHandler.java`)

| Exception | Status | Body |
|-----------|--------|------|
| `MethodArgumentNotValidException`, `ConstraintViolationException` (list bodies on `@Validated` controllers), `HandlerMethodValidationException` | 400 | `{status, message: "Dados inválidos", timestamp, errors: {field: message}}` (list items as `results[0].team1Score`) |
| `HttpMessageNotReadableException` (malformed JSON, unknown enum value) | 400 | `ErrorResponse` "Corpo da requisição inválido" |
| `MissingServletRequestParameterException`, `MissingServletRequestPartException`, `MethodArgumentTypeMismatchException` | 400 | `ErrorResponse` |
| `MaxUploadSizeExceededException` | 413 | `ErrorResponse` |
| `DataIntegrityViolationException` | 409 | `ErrorResponse` (logged as warning) |
| `HttpRequestMethodNotSupportedException` / `NoResourceFoundException` | 405 / 404 | `ErrorResponse` |
| `AppException` | its status | `ErrorResponse {status, message, timestamp}` |
| any other `Exception` | 500 | `ErrorResponse` "Ocorreu um erro inesperado", logged with `log.error("Unhandled", ex)` |

The 401s from the security entry point and `JwtAuthFilter`, and the 429 from `AuthRateLimitFilter`, are hand-built maps with the same `{status, message, timestamp}` keys. The frontend reads `message` (and `errors` for validation). All messages are in Portuguese.

Rules:
- Throw `AppException` for every expected failure (message in Portuguese); never let a `NullPointerException` or FK violation be the error.
- Add handlers to `GlobalExceptionHandler` for framework exceptions instead of try/catch in services.

## API reference

All routes are under `/api/v1`. "Auth" is the relationship checked (see the matrix); every route except `auth/**` and `files/**` needs a token.

### Auth (`AuthController`)
| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | `/auth/register` | `RegisterRequestDTO` | 201 `AuthResponseDTO {token, user}` |
| POST | `/auth/login` | `LoginRequestDTO` | 200 `AuthResponseDTO`; 429 after 10/min per IP |
| POST | `/auth/forgot-password` | `ForgotPasswordRequestDTO {email}` | 204 always (also for unknown e-mails); sends the reset e-mail when the account exists |
| POST | `/auth/reset-password` | `ResetPasswordRequestDTO {token, newPassword ≥ 8}` | 204; 400 when the token is unknown, used or expired |

### Files (`FileController`)
| Method | Path | Auth | Response |
|--------|------|------|----------|
| GET | `/files/{filename}` | public | the image (content type from extension) |

### Peladas (`PeladaController`, `/peladas`)
| Method | Path | Body | Auth | Response |
|--------|------|------|------|----------|
| POST | `/peladas` | `CreatePeladaRequestDTO` (`dayOfWeek` `MONDAY`..`SUNDAY`, `timeOfDay` `HH:mm`, 2–10 teams, 2–20 players per team, optional `timeZone` (IANA, default `America/Sao_Paulo`, unknown → 400), `maxMensalistas` ≥ 0 (default 0, above the capacity → 409)) | any | 201 `PeladaResponseDTO` |
| GET | `/peladas/my` | — | self | `List<PeladaResponseDTO>` with `memberCount`, `isAdmin` (the caller administers it) `timeZone` and `nextDaily` (`{id, date, time, status, confirmedCount, capacity, isConfirmed, startsAt, confirmOpensAt, bump}`: the next `SCHEDULED`/`CONFIRMED` session from today, or null; `capacity` = `numberOfTeams × playersPerTeam`, `isConfirmed` is the caller's attendance, `startsAt` the session start (ISO instant), `confirmOpensAt` from when the caller may confirm themselves (null for mensalistas), `bump` the caller's unseen `AttendanceBumpDTO` or null). Grouped queries, constant count |
| GET | `/peladas/{id}` | — | member | `PeladaDetailResponseDTO` (includes `numberOfTeams`, `playersPerTeam`, `timeZone`, `maxMensalistas` and `members` with `isAdmin`, `isMensalista` and their `stars` in this pelada) |
| PUT | `/peladas/{id}` | `UpdatePeladaRequestDTO` (partial; `dayOfWeek` `MONDAY`..`SUNDAY`, `timeOfDay` `HH:mm`, `timeZone` (unknown → 400), `numberOfTeams` 2–10, `playersPerTeam` 2–20, `maxMensalistas` ≥ 0 (409 below the current mensalistas or above the resulting capacity: the limit, new or current, is checked against the new numbers under the pelada row lock)) | admin | `PeladaResponseDTO`. A new capacity also applies to the sessions already scheduled: nobody is removed (a session with more confirmed players is just over the limit) and sorted teams stay |
| PUT | `/peladas/{id}/creator` | `TransferCreatorRequestDTO {userId}` (`@NotNull`) | creator | `PeladaResponseDTO`; `userId` must be a member (404) and an admin (409 "Só é possível transferir a pelada para um admin"); yourself → 400. The old creator stays member and admin |
| DELETE | `/peladas/{id}` | — | creator | 204; deletes dailies (results, teams, attendance, photos), messages and rankings, then rebuilds the players' global Stats |
| POST | `/peladas/{id}/players` | `AddPlayerRequestDTO {userId}` | admin | 200; 409 if already a member |
| DELETE | `/peladas/{id}/players/{userId}` | — | admin | 200; 404 if `userId` isn't a member. Also deletes their stars and mensalista row, and takes them out of the attendance and teams of the pelada's sessions that haven't started (`SCHEDULED`/`CONFIRMED`); live, finished and canceled sessions keep them, and their results, rankings and stats stay |
| PUT | `/peladas/{id}/players/{userId}/admin` | `SetAdminRequestDTO {isAdmin}` | admin | 200; 404 if `userId` isn't a member |
| PUT | `/peladas/{id}/players/{userId}/mensalista` | `SetMensalistaRequestDTO {isMensalista}` (`@NotNull`) | admin | `PeladaMemberDTO` with the new type; idempotent; 404 if `userId` isn't a member; 409 when the limit is full |
| PUT | `/peladas/{id}/players/{userId}/stars` | `UpdatePlayerStarsRequestDTO {stars 1–5}` | admin | `PeladaMemberDTO` with the new stars; 404 if `userId` isn't a member |
| PUT | `/peladas/{id}/players/admin` | `BulkSetAdminRequestDTO {userIds, isAdmin}` | admin | `List<PeladaMemberDTO>` (the targets, request order). Bulk version of `.../{userId}/admin`; idempotent per member. `userIds`: 1–100, no duplicates (400). Atomic: a target that isn't a member → 404 naming the player, the creator → 403, and nothing changes |
| PUT | `/peladas/{id}/players/mensalista` | `BulkSetMensalistaRequestDTO {userIds, isMensalista}` | admin | `List<PeladaMemberDTO>`; pelada row locked like the single route; only members who change count towards the limit, and a batch past it → 409 "Limite de mensalistas atingido: cabem 2, foram selecionados 5" (a single new mensalista keeps "(8/8)"); atomic |
| PUT | `/peladas/{id}/players/stars` | `BulkSetStarsRequestDTO {userIds, stars 1–5}` | admin | `List<PeladaMemberDTO>`; the same stars for every target, creator and caller included; atomic |
| POST | `/peladas/{id}/players/remove` | `BulkRemovePlayersRequestDTO {userIds}` | admin | 204; same effects as the single removal for every target (batch queries: stars and mensalista rows, attendance and teams of the upcoming sessions through `DailyService.removeFromUpcomingSessions(pelada, users)`); the creator in the list → 403; atomic. POST because a DELETE with a body is poorly supported by proxies and axios |
| GET | `/peladas/{id}/ranking` | — | member | `List<RankingDTO>` |
| GET | `/peladas/{id}/members/{userId}/stats` | — | member (404 if `userId` isn't a member) | `PlayerPeladaStatsDTO` (includes the player's `stars` in this pelada) |
| GET | `/peladas/{id}/members/{userId}/history?limit=` | — | member (404 if `userId` isn't a member) | `PlayerPeladaHistoryDTO {userId, totalSessions, rows: [{dailyId, date, goals, assists, matchesPlayed, wins, wonSession}]}`, newest first, one row per finalized session the player took part in. `limit` (optional, 1–100, else 400) returns only the last N sessions; without it every session is returned. `totalSessions` is always the full count (the count query only runs when the page is full) |
| GET | `/peladas/{id}/attendance` | — | member | `PeladaAttendanceDTO {from, to, totalSessions, rows: [{userId, username, userImage, sessionsAttended, rate}]}`: the last 6 months (`to` = today). `totalSessions` counts the pelada's `FINISHED` dailies in the period; a player attended a session when they have a `UserDailyStats` row in it (was on a team). One row per current member (former members are left out) with `isMensalista` (the member's current type), `rate` = attended / total (0 when there are no sessions), sorted by rate, then attended, then username. Three queries (count + grouped count + mensalista ids), no per-member query |
| GET | `/peladas/{id}/awards` | — | member | `PeladaAwardsDTO` |
| GET | `/peladas/{id}/messages?page=0&size=50` | — | member | `List<MessageDTO>` (oldest first); `size` is clamped to 1–100, `page` to ≥ 0 |
| POST | `/peladas/{id}/image` | multipart `file` | admin | `PeladaResponseDTO` |
| DELETE | `/peladas/{id}/image` | — | admin | `PeladaResponseDTO` with `image` null; the file is deleted after the commit; idempotent |

### Dailies (`DailyController`)
| Method | Path | Body | Auth | Response |
|--------|------|------|------|----------|
| POST | `/peladas/{peladaId}/dailies` | `CreateDailyRequestDTO` | admin | 201 `DailyListItemDTO` |
| GET | `/peladas/{peladaId}/dailies` | — | member | `List<DailyListItemDTO>` (`confirmedPlayerCount`, `teamCount`, `matchCount`, `championImage`, `isConfirmed` = the caller's attendance, `capacity`, `startsAt`, `confirmOpensAt` (null for mensalistas); grouped queries, constant count) |
| GET | `/dailies/{id}` | — | member | `DailyDetailDTO` (includes `isAdmin` for the caller; `peladaMembers`, every member's public fields with `isMensalista`, for the attendance list; `confirmedPlayers` with `isMensalista` and `confirmedAt`; `capacity`, `timeZone`, `startsAt`, `confirmOpensAt` (null for mensalistas); `myBump` (the caller's unseen bump or null) and `bumps` (`[{user, bumpedBy, bumpedAt}]`, public fields, newest first)) |
| POST / DELETE | `/dailies/{id}/confirm` | — (a body is ignored) | member / confirmed | `DailyListItemDTO`; POST: 403 when a diarista's window isn't open, 409 "Sessão lotada" for a diarista in a full session; a mensalista in a full session bumps the diarista confirmed last |
| POST | `/dailies/{id}/bumps/seen` | — | member | 204; marks the caller's bumps in that session as seen (other users' untouched) and reads the caller's `DIARISTA_BUMPED` notification of that session |
| POST | `/dailies/{id}/confirm/all` | — | admin | `DailyListItemDTO`; confirms every member who hasn't confirmed (400 when the status is locked) |
| POST / DELETE | `/dailies/{id}/confirm/{userId}` | — | admin | `DailyListItemDTO`; no window or capacity check, never bumps |
| POST | `/dailies/{id}/sort-teams` | — | admin | `List<TeamDTO>` |
| PUT | `/dailies/{id}/teams/swap` | `SwapPlayersRequestDTO` | admin | `List<TeamDTO>` |
| PATCH | `/dailies/{dailyId}/teams/{teamId}/name` | `UpdateTeamNameRequestDTO` | team player | `TeamDTO` |
| PATCH | `/dailies/{dailyId}/teams/{teamId}/color` | `UpdateTeamColorRequestDTO` | team player or admin | `TeamDTO` |
| PUT | `/dailies/{id}/status` | `UpdateDailyStatusRequestDTO {status: DailyStatus}` | admin | `DailyListItemDTO` |
| POST | `/dailies/{id}/results` | `List<MatchResultDTO>` (non-empty; the list is the session's full set of matches: saved matches left out are deleted; a `matchId` must belong to this daily, else 404, and appear once; stats only for players of the two teams) | admin | `List<MatchDTO>`; on a FINISHED daily it also recomputes `UserDailyStats`, Artilheiro/Garçom and Ranking/Stats, keeping the Puskás/Bola Murcha winners |
| POST | `/dailies/{id}/finalize` | `FinalizeDailyRequestDTO {puskasWinnerIds, wiltballWinnerIds}` (winners must be players on the session's teams) | admin | `DailyDetailDTO`; session stats are built for the players on the teams (not the confirmed list, which can change after the sort), and players of a previous finalize are rebuilt too |
| POST | `/dailies/{id}/populate` | `PopulateDailyRequestDTO` (unique team colors, each player once, all members, each match between two different teams) | admin | `DailyDetailDTO` |
| PUT | `/dailies/{id}/champion-image` | multipart `file` | admin | `DailyListItemDTO` |
| DELETE | `/dailies/{id}` | — | admin | 204; rebuilds Ranking/Stats when the daily was finalized |

### Notifications (`NotificationController`, `/notifications`)
Every route acts on the caller's notifications only, except unsubscribe / resubscribe, which are public and act on the user and category signed in the e-mail's token.

| Method | Path | Body | Response |
|--------|------|------|----------|
| GET | `/notifications?page=0&size=20&unread=false` | — | `List<NotificationDTO {id, type, category, award, createdAt, readAt, pelada {id, name, image}, daily {id, date, time}, actor (PublicUserDTO), team {name, color}}>`, newest first; `size` clamped to 1–50, `page` ≥ 0; `unread=true` returns only unread ones. `team` is the caller's team, only on `TEAMS_SORTED`. Two queries (page with fetch joins + the caller's teams of those sessions) |
| GET | `/notifications/unread-count` | — | `{count}` |
| POST | `/notifications/{id}/read` | — | 204; idempotent; missing or someone else's → 404. A `DIARISTA_BUMPED` notification also sets the bump's `seenAt` |
| POST | `/notifications/read-all` | — | 204; also sets `seenAt` on the bumps of the `DIARISTA_BUMPED` notifications it reads |

| GET | `/notifications/preferences` | — | `List<NotificationPreferenceDTO {category, emailEnabled}>`: always the four categories, defaults applied |
| PUT | `/notifications/preferences` | `List<NotificationPreferenceDTO>` (1–4 items, `category` and `emailEnabled` `@NotNull`; a repeated category → 400) | the four categories after the change. Partial: only the categories sent change; a value equal to the default deletes the row instead of storing it |
| POST | `/notifications/unsubscribe` | `UnsubscribeRequestDTO {token}` (JSON, from the client's page) **or** `?token=` with any body (RFC 8058 one-click from the mail provider, form body `List-Unsubscribe=One-Click`) | **public**, rate-limited; `NotificationPreferenceDTO` with `emailEnabled: false`; idempotent; a tampered token, a swapped category or an unknown user → 400 "Link inválido" |
| POST | `/notifications/resubscribe` | `UnsubscribeRequestDTO {token}` | **public**, rate-limited; the same token turns the category back on |

Types and recipients (the actor is always left out): `DIARISTA_BUMPED` (bumped diarista), `SESSION_CANCELED` (confirmed players), `ATTENDANCE_CONFIRMED_BY_ADMIN` / `ATTENDANCE_REMOVED_BY_ADMIN` (the player; "Confirmar todos" included), `SESSION_CREATED` (every member; from the scheduler with no actor), `TEAMS_SORTED` / `SESSION_FINISHED` (players on the teams; collapsed while unread), `AWARD_WON` (each winner per award, first finalize only), `ADDED_TO_PELADA`, `REMOVED_FROM_PELADA`, `ADMIN_GRANTED` / `ADMIN_REVOKED`, `MENSALISTA_GRANTED` / `MENSALISTA_REVOKED` (members whose status changed), `PELADA_TRANSFERRED` (new creator). Scheduled by `ScheduledNotificationJob` (every 15 min, no actor), for `SCHEDULED` / `CONFIRMED` sessions whose diarista window has opened (`AttendanceWindow.diaristaOpensAt`, start − 24 h in the pelada's zone) and that haven't started: `SESSION_REMINDER` (mensalistas who haven't confirmed) and `DIARISTA_WINDOW_OPEN` (every other member who hasn't confirmed, creator and admins included, only while confirmed < capacity). Each is created once per recipient and session. Every session in the window is picked, not just the ones that opened since the last run, so a run missed while the API was down is caught up; a session created after its window had opened is skipped (`SESSION_CREATED` already told everyone). Importing teams from a message (`populate`) doesn't notify.

E-mail: the emailable types go by e-mail when the recipient's category is on (see [Notification e-mails](#notification-e-mails)). Not emailable: `ATTENDANCE_CONFIRMED_BY_ADMIN`, `TEAMS_SORTED`, `ADMIN_REVOKED`.

Realtime: after the commit every new or collapsed notification is sent to its recipient on the STOMP user queue `/user/queue/notifications` as `NotificationPushDTO {notification: NotificationDTO, unreadCount}` (`unreadCount` is the recipient's unread total after it, so a collapsed notification isn't counted twice). Three queries per batch (rows with fetch joins, the recipients' teams of `TEAMS_SORTED`, a grouped unread count). Nothing is replayed: a client that was offline refetches `unread-count` when it reconnects.

### Users (`UserController`, `/users`)
| Method | Path | Body | Auth | Response |
|--------|------|------|------|----------|
| GET | `/users/search?q=` | — | any | `List<PublicUserDTO {id, username, image, position}>` (max 10); matches username or email; `q` needs ≥ 3 characters (400) |
| GET | `/users/{id}` | — | any | `ProfileDTO` (`email` only for self; `stars` = the rounded average of the user's stars in every pelada they belong to, members without a row counting 3, omitted when they belong to none) |
| GET | `/users/{id}/peladas` | — | any | `List<PeladaResponseDTO>`: peladas the caller and `{id}` both belong to (`isAdmin`/`nextDaily.isConfirmed` refer to the caller) |
| PUT | `/users/{id}` | `UpdateProfileRequest {username 3–30, position}` (stars are set per pelada by admins) | self | `ProfileDTO` |
| POST | `/users/{id}/image`, `/users/{id}/background-image` | multipart `file` | self | `ProfileDTO` |
| GET | `/users/{id}/stats` | — | self or shares a pelada | `StatsDTO` |
| GET | `/users/{id}/stats/timeline?from&to` | — | self or shares a pelada | `UserStatsTimelineDTO` (other viewers: shared peladas only; `from` after `to` → 400) |
| GET | `/users/{id}/stats/matches` | — | self or shares a pelada | `UserMatchHistoryDTO` (other viewers: shared peladas only) |

When an endpoint or DTO changes, update this table, the frontend `src/api/*` + `src/types/*`, and `client/docs/FRONTEND.md` in the same change.

## Performance

- **Fetching:** everything is `LAZY`; keep it that way and load what a use case needs with `JOIN FETCH` or `@EntityGraph` (reference: `TeamRepository.findByDailyWithPlayers`, `PlayerMatchStatRepository.findByMatchInWithUser`). Never switch a relation to `EAGER`.
- **Read models:** `getMyPeladas` / `getSharedPeladas` use grouped member-count and next-session queries; `getDailiesForPelada` a grouped confirmed-count query; `getDailyDetail` fetches teams with players, matches with teams, stats with users and league entries with teams; ranking, chat and profile history use fetch joins; the attendance ranking is one count, one grouped count (`UserDailyStatsRepository.countAttendanceByUser`) and the pelada's mensalista ids; award counts are single grouped native queries (`DailyAwardRepository`). Keep new read paths at a constant number of queries.
- **Membership checks** use exists queries (`PeladaAccessHelper`), never `members.contains`.
- **Indexes:** every FK and lookup column is indexed (V3); index new ones in the same migration.
- **Aggregates:** compute in the DB with grouped queries (as finalize does: `aggregateStatsByUsers`), not by looping and querying per user.
- **Transactions:** `@Transactional` on writes, `@Transactional(readOnly = true)` on reads. `open-in-view` is on, so lazy loads in controllers silently work; don't rely on it — build DTOs inside the service transaction.
- **Pagination:** any list that grows with time (messages, dailies, match history) takes a `Pageable` with a capped `size` (chat: 1–100).
- **Image:** `core/Dockerfile` is multi-stage on `eclipse-temurin:25` (noble): Maven dependencies in their own layer, the jar extracted into layers (`jarmode=tools extract --layers`, so a code change only replaces the last one), an AOT cache (JEP 483/514) recorded in a training stage that refreshes the context without a database, a non-root user (uid 10001), `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` against the container's `mem_limit` (1g) and a `HEALTHCHECK` on readiness. Measured locally (same Postgres 17): startup 3.1 s with Java 17 / Boot 3.2, 3.1 s with Java 25 / Boot 3.5 without the cache, 1.8 s with it; the image grew from 309 MB to 532 MB (the cache is 125 MB).
- **Virtual threads:** HTTP, `@Async` and cron jobs use Boot's executors. With `spring.task.execution.mode=force`, Boot's WebSocket configurer also assigns the same `applicationTaskExecutor` to `clientInboundChannelExecutor` and `clientOutboundChannelExecutor` (verified by identity and `Thread.isVirtual()` in `VirtualThreadsTest`); `WebSocketConfig` adds no executor override. The notification e-mail dispatcher keeps its own single platform thread and pacing. Security/Hibernate ThreadLocals remain per request; there are no application `ThreadLocal` caches to migrate.
- **Hikari under load:** virtual threads do not increase the database pool (10). The five-second acquire timeout replaces Hikari's 30-second default; it bounds connection acquisition, not the whole request. Pessimistic row locks keep a connection while waiting. Watch `hikaricp.connections.pending` and acquire time together with database lock waits. Leave `spring.task.execution.simple.concurrency-limit` unset initially; set it only if production async bursts justify it. More virtual threads do not promise more throughput on these database-bound routes.
- **Local PR3 measurement (2026-10-30):** repeated JFR load at 50/200/500 connections: p95 66/245/812 ms, zero non-2xx/client errors, 28/27/27 platform threads, zero pinning events; the container finished healthy. Repeated PR2: p95 586/1313/1400 ms and 89/232/232 platform threads. The initial PR3 run had six non-2xx at 500 alongside Hikari timeouts, so monitor pending/acquire and lock waits in production. See the [full comparison and limitations](../../docs/plans/2026-10-29-java25-boot4-virtual-threads-design.md#local-load-evidence-2026-10-30) and [repeated PR3 report](../../ops/loadtest/results/pr3-repeat-2026-10-30.md). Deployment and one week in Grafana remain pending.
- **Single instance assumptions:** the in-memory STOMP broker, the rate-limit cache, the notification e-mail queue and pacer (e-mails still queued are lost on a restart) and `@Scheduled` (no ShedLock) only work with one API instance. Two instances would both run `ScheduledNotificationJob`; `uq_notifications_scheduled` makes the second insert fail and that pelada's transaction roll back (logged).
- **Scheduler:** `DailySchedulerService` runs hourly with the `Clock` read in each pelada's `timeZone`; each pelada runs in its own `TransactionTemplate` in a try/catch (reloaded by id inside it: the listed peladas are detached and `SESSION_CREATED` reads the lazy members), so a failing pelada rolls back only its own daily and notifications and is logged. `dayOfWeek` is validated on create/update. `ScheduledNotificationJob` runs every 15 min: one query for the candidate sessions (pelada fetched, a coarse date range, the exact window checked in Java), then per pelada its own `TransactionTemplate` in a try/catch (a failing pelada rolls back only its rows) with two recipient queries and one count per due session. `NotificationRetentionJob` runs at 04:00 with two bulk deletes (no index on `created_at`: a daily scan of a small table).

## Observability

- **Probes:** `/livez` and `/readyz` on the main port (public), the rest of Actuator on `management.server.port=9091` (internal, never published): `health` (with details), `info`, `metrics`, `prometheus`, `threaddump`. The image's `HEALTHCHECK` and the deploy's `healthy` wait use readiness on 9091; the `deploy.yml` smoke test uses `<API_URL>/readyz`.
- **Metrics** (Micrometer, Prometheus format at `:9091/actuator/prometheus`, tag `application=futspring`): the built-in JVM (heap, GC, threads), Hikari, Tomcat, `http.server.requests` (with percentile histograms), `tasks.scheduled.execution` and the rate-limit Caffeine cache (`cache="futspring.ratelimit"`), plus the domain ones:

  | Metric | Type | Where |
  |--------|------|-------|
  | `futspring.email.queue.size` | gauge | `NotificationEmailDispatcher` executor queue |
  | `futspring.email.sent` (`result=ok\|failed`) | counter | `NotificationEmailDispatcher`, each send |
  | `futspring.email.dropped` | counter | `NotificationEmailDispatcher`, full queue |
  | `futspring.ratelimit.rejected` (`route`) | counter | `AuthRateLimitFilter`, each 429 |
  | `futspring.notifications.push` (`result=ok\|failed`) | counter | `NotificationPushService`, each STOMP send |

- **Stack:** Prometheus + Grafana + node-exporter + cAdvisor in `ops/observability/` (README there), shared by the VPS, deployed by `deploy-observability.yml`. The API joins the external `observability` network and is scraped at `futspring-api:9091` (the compose service name: Tomcat rejects the container name's underscore in `Host`). Grafana at `https://grafana.luisgosampaio.com` (login only) has the provisioned **Futspring** dashboard (requests, latency, errors, JVM, threads, Hikari, e-mail, pushes, 429s, container CPU/RAM) plus JVM (Micrometer), Node Exporter Full and cAdvisor.
- **Load test:** `ops/loadtest/run.sh <label>` builds the image and runs it locally with the production limits (`--cpus=1 --memory=1g`), Postgres 17 and the dev seed, logs in as the seed admin and runs `oha` with a random mix of `GET /peladas/my`, `/peladas/{id}`, `/dailies/{id}`, `/peladas/{id}/ranking`, `/peladas/{id}/attendance` at 50 / 200 / 500 connections for 60 s each (`CONCURRENCY`, `DURATION`, `SKIP_BUILD`, `IMAGE` override). Needs Docker, oha, Python 3 and Node 22+ (the native WebSocket STOMP probe). A real STOMP CONNECT / SUBSCRIBE / SEND / MESSAGE against the disposable seed runs before warm-up, followed by an internal actuator thread dump; no production route is added. The table (RPS, p50/p95/p99, non-2xx, sampled live platform threads and Hikari pending) goes to `ops/loadtest/results/<label>-<date>.md`; `JFR=1` records `jdk.VirtualThreadPinned` with a **0 ms threshold**, counting all events, those **>20 ms**, and their maximum duration (`jfr-summary.py` parses ISO-8601 durations including minutes/hours). Use a local JDK 25 (`JAVA_HOME`) to read the recording. The report includes image ID, container health, HTTP status distribution, exception counts and timeout durations; the full synthetic API log is retained locally as a gitignored `*-api.log`. `jvm.threads.live` and the actuator dump show platform threads only; absence from that dump does not prove virtual execution (the integration test verifies it). Never against production. Local acceptance and the comparison with PR1/PR2 are recorded in the PR3 design entry; deployment and one week of Grafana observation remain separate acceptance steps.

## Testing

Tests live in `src/test/java/com/futspring/backend`, in the same domain packages as the code they test (`domain/daily/DailyServiceTest`, `domain/stats/repository/UserDailyStatsRepositoryTest`…); shared test helpers (`BaseIntegrationTest`, `MembershipStubs`) are in `support/`, migration tests at the root.

- **Service unit tests** — JUnit Jupiter 6 + Mockito + AssertJ, one class per service (`PeladaServiceTest`, `DailyServiceTest`, `DailyAttendanceServiceTest`…). Mock repositories and `UserAuthenticationHelper`; build a real `PeladaAccessHelper` over the mocked `PeladaRepository` and call `MembershipStubs.stubMembership(peladaRepository, pelada…)` so the exists queries answer from the entities' member/admin sets.
- **Controller integration tests** — extend `BaseIntegrationTest` (`@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureMockMvc`, `@Transactional`, real JWTs from `JwtService`).
- **Repository tests** — `@DataJpaTest` on H2 (`UserRepositoryTest`, `DailyRepositoryTest`, `UserDailyStatsRepositoryTest`, `DailyAwardRepositoryTest` for the native award queries).
- **Attendance** — `DailyAttendanceServiceTest` uses `Clock.fixed` and `support/InMemoryAttendance` (lenient stubs that back `DailyAttendanceRepository` with an in-memory list); `AttendanceWindowTest` covers zones and the Canadian DST change; `DailyAttendanceRepositoryTest` the victim query; `AttendanceConcurrencyTest` (Testcontainers Postgres, no test transaction, skipped without Docker) starts eight virtual-thread diaristas behind one latch for the last spot and expects one confirmation and seven conflicts.
- **Notifications** — `NotificationServiceTest` (recipients per type, actor excluded, collapse, awards only on the first finalize, delivery only after commit, through `TransactionSynchronizationManager`); `NotificationPushServiceTest` (each recipient's e-mail and unread count, the team on `TEAMS_SORTED`, one failed send doesn't stop the others, failures only logged); `NotificationControllerTest` (REST, the bump `seenAt` ↔ `readAt` sync both ways, one event per path end to end, deleting a daily / pelada with notifications on H2, preferences GET/PUT and validation, public unsubscribe / resubscribe with no session, a stale session header, the one-click form POST, invalid tokens and the rate limit); `NotificationEmailContentTest` (every type renders subject, title, body and CTA; escaping and one-pass placeholders); `NotificationEmailDispatcherTest` (send matrix type × preference × default, headers, pacing per send, failures only logged, nothing queued without a real sender); `EmailPacerTest` (fake clock: never more than 2 sends in a second); `UnsubscribeTokensTest` (tampered, swapped category or user, another secret); `NotificationRepositoryTest` (scheduled recipients: mensalista vs diarista, creator and admins as diaristas, confirmed and already-notified left out, scoped to the session); `ScheduledNotificationJobTest` (`@DataJpaTest` on H2 with `Clock.fixed`: recipients, full session, locked / canceled / started sessions, the window edge, idempotent runs through `NOT EXISTS` alone, the Toronto DST change, a session created inside its window, a failing pelada not stopping the others, nothing delivered without a commit); `NotificationRetentionJobTest` (the 90 / 180-day boundaries); `DailySchedulerTransactionTest` (`@DataJpaTest` with no test transaction: a failing pelada rolls back alone, the other commits its daily and `SESSION_CREATED`, delivered after the commit). The publishing services' tests verify the events they publish.
- **WebSocket** — `JwtChannelInterceptorTest` covers CONNECT/SUBSCRIBE/SEND rules (the notification queue included).
- **Contract** — `contract/ContractSnapshotTest` (`@JsonTest`, the application's `JsonMapper`) guards the JSON the web app reads. Every top-level response DTO, filled by `ContractFixtures` (every field set from its name and type, so a class turned into a record gets the same values), must serialize to `src/test/resources/contract/<Dto>.json`, compared as trees (property order doesn't matter); every request body the client sends (`contract/requests/<Dto>.json`, taken from `client/src/api`) must deserialize and keep every value. A new response field fails the test: regenerate with `-Dcontract.update=true` only for an intended change and review the diff against `client/src/types`. The snapshots record today's quirk: a primitive `boolean isX` field with `@JsonProperty("isX")` is written twice, as `isX` and as `x` (from Lombok's `isX()` getter); the client only reads `isX`.
- **Tokens and docs** — `JwtServiceTest` keeps a token signed by the old JJWT 0.11 code (HS256, test secret, expiring in 2100): it must keep validating with the same claims, so a library upgrade never logs everyone out; tokens stay HS256 even with a 64-byte secret (JJWT would otherwise pick HS512). `OpenApiDocsTest`: docs not public by default, public with the `bearer` scheme when enabled.
- **Virtual threads** — `VirtualThreadsTest` uses test-only imported HTTP, `@Async` and one-shot `@Scheduled` probes, with no executor bean or production endpoint. A real HTTP socket request verifies Tomcat's virtual thread (MockMvc cannot); async and scheduler callbacks check `Thread.currentThread().isVirtual()`. Both STOMP executor beans are the same virtual-thread application executor. It also verifies Hikari's ten-connection/five-second limits.
- **Actuator** — `ActuatorExposureTest` (`@AutoConfigureMetrics`, `@AutoConfigureTestRestTemplate`): `/livez`, `/readyz` public on the main port, no actuator endpoint served there, `/actuator/prometheus` and health details on the management port without a token. The domain counters are checked in `NotificationEmailDispatcherTest`, `NotificationPushServiceTest` and `AuthControllerTest` (`SimpleMeterRegistry` / the context's registry).
- **Migration tests** — `FlywayMigrationTest` applies every migration to a Postgres container and starts the app with `validate`; `MigrationDataTest` migrates to a version, inserts production-shaped rows and checks what the next migration does. A new migration or entity change must keep them green (needs Docker).
- When adding an endpoint: a controller test (happy path, validation 400, 403 for non-member/non-admin, 404) and service tests for the rules, including the ownership cases listed in [Rules for new code](#rules-for-new-code).
- Mockito strict stubs are on: remove stubs a test doesn't use (`UnnecessaryStubbingException`).
- The suite is green and CI blocks the deploy on any failure; keep it that way.

## Conventions

- Constructor injection with Lombok `@RequiredArgsConstructor`; no field `@Autowired`.
- `@Valid` on every `@RequestBody`; constraints (`@NotBlank`, `@Size`, `@Min`, `@NotNull`) on every request DTO field that has a rule. Nested lists need `@Valid` on the element type too (`List<@Valid MatchResultDTO>`).
- Read the principal with `@AuthenticationPrincipal String email` (every controller does).
- New enum-like fields are Java enums with `@Enumerated(EnumType.STRING)`.
- Explicit `@Table(name = ...)` and snake_case `@Column(name = ...)` on new entities/columns.
- Entities: prefer `@Getter @Setter` + `@NoArgsConstructor`/`@AllArgsConstructor`/`@Builder` over `@Data`, with `equals`/`hashCode` on the id only (see antipatterns).
- Logging with `@Slf4j`; no `System.out`/`printStackTrace`.
- Code and identifiers in English. `AppException` and validation messages reach the user (the frontend toasts `message`), so new ones are written in Portuguese; existing English messages ("Daily not found", "Only admins can…") are translated when touched.

## Patterns

Follow these when writing new code. Each points to a file that does it right.

### 1. Thin controller, one service call

```java
@PutMapping("/{id}")
public ResponseEntity<ProfileDTO> update(@PathVariable Long id,
                                         @Valid @RequestBody UpdateProfileRequest request,
                                         @AuthenticationPrincipal String email) {
    return ResponseEntity.ok(userService.updateProfile(id, request, email));
}
```

Reference: `UserController`, `PeladaController.createPelada`.

### 2. Authorization in the service, before any change

Load the caller, load the resource, check the relationship, then act (see [The pattern](#the-pattern)). Reference: `PeladaService.updatePelada`, `DailyTeamManagementService.updateTeamName` (also scopes the team to the daily), `DailyResultsService.submitResults` (scopes `matchId` with `findByIdAndDaily`).

### 3. Map to DTOs with static factories

```java
public static PeladaResponseDTO from(Pelada pelada) { ... }
```

Reference: `PeladaResponseDTO`, `RankingDTO`, `MessageDTO`. Mapping that needs several repositories lives in the service or a `@Component` mapper (`DailyDTOMapper`).

### 4. Domain errors with `AppException`

```java
throw new AppException(HttpStatus.CONFLICT, "Jogador já está na pelada");   // new messages in pt-BR
```

### 5. Batch queries for aggregates

Group and sum in one JPQL query and map the rows, instead of one query per user. Reference: `UserDailyStatsRepository.aggregateStatsByUsers` used by finalize.

### 6. Fetch joins for read models

Reference: `TeamRepository.findByDailyWithPlayers`, `PlayerMatchStatRepository.findByMatchInWithUser`, `UserDailyStatsRepository.findHistoryByUserAndPelada`.

### 7. One service per sub-domain

Reference: the daily services (`DailyService`, `DailyAttendanceService`, `DailyTeamManagementService`, `DailyResultsService`, `DailySchedulerService`).

### 8. Shared helpers instead of copy-paste

Caller lookup through `UserAuthenticationHelper`, relationship checks through `PeladaAccessHelper`, uploads through `FileUploadService`, player/team/match mapping through `DailyDTOMapper`, Ranking/Stats rebuild through `AggregateRebuildService`.

## Antipatterns

Don't introduce these. Some exist already ("Found in"); fix them when the change is small, otherwise mention them to the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 1 | `@RequestBody` without `@Valid` (constraints in the DTO are never enforced: negative scores are accepted) | `@Valid @RequestBody`, constraints on the DTO | — |
| 2 | Trust a child id from the request without checking it belongs to the route's parent (IDOR) | Scope it: `match.getDaily().getId().equals(dailyId)` or a scoped query (`findByIdAndDaily`) | — |
| 3 | Endpoints that read another user's data with no relationship check, or expose email | Check the relationship; return a public DTO without PII (`PublicUserDTO`) | — |
| 4 | Unauthorized WebSocket subscriptions / anonymous CONNECT | Authorize `SUBSCRIBE` to `/topic/pelada/{id}` for members only; reject CONNECT without a valid token | — |
| 5 | Secrets in tracked or deployable files (DB password, JWT secret, Supabase URL) | Environment variables / `.env` (gitignored); rotate anything that leaked | `docker-compose.dev.yml` (dev values) |
| 6 | Multi-line `"..."` string in `@Query` (doesn't compile) or mismatched aliases | Text block `"""..."""`, one alias | — |
| 7 | Catch-all handler that swallows the exception without logging | `log.error` in the catch-all; specific handlers for framework exceptions | — |
| 8 | Lombok `@Data` on entities (equals/hashCode over every field and lazy collections; `toString` triggers lazy loads) | `@Getter @Setter`, id-based equals/hashCode, no collection in `toString` | — |
| 9 | Magic strings for status/position duplicated across services | Java enums (`DailyStatus`, `Position`) with `EnumType.STRING`, one shared set of locked statuses | `User.position` (a validated string) |
| 10 | Copy-pasted business logic | Extract a method/service | — |
| 11 | Business logic or several service calls in a controller | One service method; filters for cross-cutting concerns | `DailyController` finalize/populate call the mutating service, then `getDailyDetail` (kept so the response is read in its own transaction) |
| 12 | In-memory, never-evicted rate-limit map keyed by the proxy IP | A filter with an evicting cache (Caffeine) and the real client IP (`server.forward-headers-strategy=framework`) | — |
| 13 | Upload extension taken from the client filename; old image deleted before the transaction commits; replaced images never deleted | Extension from the validated content type; delete after commit (`TransactionSynchronization`); delete the previous file | — |
| 14 | Free-text fields that code later parses (`DayOfWeek.valueOf`) | Validate on input (enum or `@Pattern`); isolate failures per item in jobs | — |
| 15 | Deleting a parent that still has children (FK violation → 500) | Delete or cascade children explicitly in the service, or forbid with 409 | — |
| 16 | `EAGER` fetches, per-row lazy loads in loops, loading member sets for `contains` | Fetch joins, count/exists queries, projections | — |
| 17 | Unbounded lists / uncapped page size | `Pageable` with a max size | — |
| 18 | Two CORS sources that disagree | One `ALLOWED_ORIGINS` list used by MVC, Security (`http.cors()`) and WebSocket | — |
| 19 | Schema changes made only by editing entities and relying on `ddl-auto=update` in production | Flyway migration in the same PR; `validate` in prod | — |
| 20 | New `NOT NULL` column without a default/backfill, or a rename in one step | Expand/contract (see [Migration rules](#migration-rules)) | — |
| 21 | Editing a migration that already ran | New migration | — |
| 22 | Seeding known-password users whenever the profile isn't `prod` | Seed only with an explicit `dev` profile | — |
| 23 | Dead code left around | Delete it | — |
