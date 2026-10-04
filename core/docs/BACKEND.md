# Backend Documentation — Futspring API

Spring Boot REST API in `core/`. Base package: `com.futspring.backend` (`src/main/java/com/futspring/backend`, written `S/` below).

Futspring manages amateur soccer groups ("peladas"): members and admins, match-day sessions ("dailies") with attendance, team sorting, match results, league table, awards, a pelada-wide ranking, per-player stats and a live chat.

## Stack

- Java 17, Spring Boot 3.2.3, Maven (`./mvnw`)
- Spring Web MVC, Spring Data JPA (Hibernate), Spring Security, Bean Validation
- Spring WebSocket — STOMP over SockJS, in-memory simple broker (chat)
- PostgreSQL 15 (runtime, Supabase in production), H2 in PostgreSQL mode (tests)
- JJWT 0.11.5 (HS256), Bucket4j 8.7 + Caffeine (login/register rate limit, in memory), Lombok
- Tests: JUnit 5, Mockito, AssertJ, spring-security-test, Testcontainers 1.21 (overridden in `pom.xml`: Boot's 1.19 can't talk to Docker 29)
- Flyway 9 (schema migrations, see [Database schema & migrations](#database-schema--migrations))
- Not present: Swagger/springdoc, Actuator, Redis, caching, MapStruct/ModelMapper

## Commands

Run from `core/`:

```bash
./mvnw spring-boot:run                           # needs DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET
./mvnw test                                      # all tests (H2, src/test/resources/application.properties)
./mvnw test -Dtest=PeladaServiceTest             # one test class
./mvnw clean package -DskipTests                 # build jar
docker compose up --build                        # API + postgres:15-alpine (docker-compose.yml, dev)
```

CI (`.github/workflows/ci-backend.yml`) runs `./mvnw -B verify` (every test, including `FlywayMigrationTest` and `MigrationDataTest`) on pushes and PRs to `main`/`dev` that touch `core/`. Run `./mvnw test` before finishing any backend change.

### CI/CD

- `.github/workflows/deploy.yml` runs on every push to `main` (and manually): it calls both CI workflows, writes `.env` from the GitHub secrets, runs `scripts/deploy.sh`, then smoke-tests `APP_URL` (200) and `<API_URL>/api/v1/files/__smoke-test__.png` (404 from `FileController`). A protected route can't be used: Spring Security answers 401 on any unknown path, so it wouldn't catch a wrong `API_URL` prefix.
- `scripts/deploy.sh` (tracked, no secrets) builds the frontend with bun and an amd64 Docker image, rsyncs `client/dist` to `/var/www/futspring`, copies the image, `core/docker-compose.prod.yml` and `.env` to `~/projects/futspring` on the VPS, backs up the database with `pg_dump` (when `PG_DUMP_URL` is set, last 10 kept in `backups/`), starts the stack with `docker compose` (API on `127.0.0.1:${API_PORT:-8081}`, uploads in `/opt/futspring-uploads`) and waits up to 2 min for the 401 that proves Flyway and `validate` passed; otherwise it prints the logs and fails. It refuses a `.env` containing `DDL_AUTO`.
- Locally: put the production values in `.env` at the repo root (gitignored, keys in `core/.env.example`) and run `VPS_IP=… VITE_API_URL=… ./scripts/deploy.sh`.

| GitHub | Name | Notes |
|--------|------|-------|
| secret | `VPS_SSH_KEY`, `VPS_SSH_KNOWN_HOSTS` | deploy key; `ssh-keyscan -H <ip>` (falls back to keyscan when empty) |
| secret | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` | required |
| secret | `PG_DUMP_URL` | optional; libpq URL on the Supabase session pooler (5432) for the pre-migration backup |
| variable | `VPS_IP`, `APP_URL` | required |
| variable | `VPS_USER` (`root`), `API_URL` (baked in as `VITE_API_URL`, defaults to `APP_URL`; production is `https://futspring.luisgosampaio.com/api` because nginx's `location /api/` strips the prefix before proxying to `:8081`), `ALLOWED_ORIGINS` (defaults to `APP_URL`), `API_PORT` (`8081`), `JWT_EXPIRATION_MS` | optional |

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
| `SPRING_PROFILES_ACTIVE` | `dev` runs the seed (`docker-compose.dev.yml`), `prod` in production | — |

- Uploads go to `app.uploads.dir=/app/uploads` (container path; override it when running outside Docker). Multipart limit is 10 MB, but `FileUploadService` enforces 5 MB.
- `server.forward-headers-strategy=framework`: behind nginx, `getRemoteAddr()` is the client IP (rate limiting). The API only listens on `127.0.0.1`.
- Hibernate: `show-sql=false`, `batch_size=50`, ordered inserts/updates. `spring.jpa.open-in-view` is not set, so it is **on** (default).
- `DataInitializer` (`@Profile("dev")`) seeds an empty database with 20 users (password `senha123`; admin `leal@futspring.com`), the pelada "Pelada do Fut", 7 weekly finished dailies and 1 scheduled daily for today. Each finished daily gets teams (4 teams of 5, or 3 teams / 15 confirmed players in two of the weeks so histories differ), 6 matches with random results from a fixed seed (`new Random(2026)`), and is closed through `DailyResultsService.submitResults` + `finalizeDaily`, so match stats, league table, `UserDailyStats`, `Ranking`, `Stats` and awards follow the real rules.
  It only runs with the `dev` profile, never in tests or production.
- Test config (`src/test/resources/application.properties`): H2 PostgreSQL mode, `create-drop`, Flyway disabled, fixed test JWT secret, 1 h TTL, uploads in a temp dir.
- **Secrets:** all credentials come from the environment (`.env`, `.env.example` documents the keys). Never write a password, DB URL with credentials, or JWT secret into a tracked file, script, compose file or doc.

## Package layout

Features live under `domain/`, one package per domain (`domain/daily`, `domain/pelada`…). Each domain holds its controller, services, entities and repositories at its root, with request/response classes in `dto/`; the two big domains (`daily`, `stats`) also split `entity/` and `repository/`. Code used by every domain lives in `shared/`; the dev seed in `dev/`.

```
com.futspring.backend
├── FutSpringApplication        main class, @EnableScheduling
├── shared/
│   ├── config/                 SecurityConfig (filter chain, BCrypt), CorsConfig (ALLOWED_ORIGINS + CorsConfigurationSource, used by
│   │                           SecurityConfig and WebSocketConfig), JwtConfig (@Value secret + TTL), WebSocketConfig (STOMP /ws, SockJS,
│   │                           broker /topic, app prefix /app, JwtChannelInterceptor)
│   ├── exception/              AppException (RuntimeException + HttpStatus), ErrorResponse, GlobalExceptionHandler
│   ├── helper/                 UserAuthenticationHelper (getAuthenticatedUser(email) → User or 404),
│   │                           PeladaAccessHelper (isMember/isAdmin/isCreator, requireMember/requireAdmin, exists queries)
│   └── entity/EntityIdentity   id-based equals/hashCode for every entity, safe with Hibernate proxies
├── domain/                   one package per feature
│   ├── auth/                       AuthController, AuthService (register / login, issues JWT), JwtService (generate / validate / extract),
│   │   │                           JwtAuthFilter (Bearer → email principal), AuthRateLimitFilter (10/min per IP on login and register,
│   │   │                           Bucket4j + Caffeine), JwtChannelInterceptor (authenticates STOMP CONNECT/SUBSCRIBE/SEND)
│   │   └── dto/                    LoginRequestDTO, RegisterRequestDTO, AuthResponseDTO, UserResponseDTO
│   ├── user/                       UserController, UserService (profile get/update, avatar, background image), User, UserRepository
│   │   └── dto/                    ProfileDTO, PublicUserDTO, UpdateProfileRequest
│   ├── pelada/                     PeladaController, PeladaService (pelada CRUD, members, admins, user search, image), Pelada, PeladaRepository
│   │   └── dto/                    Create/UpdatePeladaRequestDTO, DayOfWeekPattern, PeladaResponseDTO, PeladaDetailResponseDTO, PeladaMemberDTO…
│   ├── daily/                      DailyController and the daily services:
│   │   │                           DailyService (create, list, status transitions, delete, detail aggregation)
│   │   │                           DailyAttendanceService (confirm / unconfirm, self and by admin)
│   │   │                           DailyTeamManagementService (sort teams with star-balanced LPT, swap, rename, color)
│   │   │                           DailyResultsService (results, live league table, finalize, champion image, populate, clearResults)
│   │   │                           DailySchedulerService (hourly cron that auto-creates the next daily), DailyDTOMapper (Player/Team/MatchDTO),
│   │                           DailyListItemAssembler (DailyListItemDTO with counts and the caller's attendance, grouped queries)
│   │   ├── entity/                 Daily, DailyStatus, Team, Match, PlayerMatchStat, LeagueTableEntry
│   │   ├── repository/             Daily, Team, Match, PlayerMatchStat, LeagueTableEntry repositories
│   │   └── dto/                    DailyDetailDTO, DailyListItemDTO, MatchResultDTO, Finalize/Populate/Swap/Update* request DTOs
│   ├── stats/                      RankingService, AwardsService, StatsService (read-side aggregates),
│   │   │                           AggregateRebuildService (rebuilds Ranking per pelada and global Stats for a set of players, batch queries)
│   │   ├── entity/                 Ranking, Stats, UserDailyStats, DailyAward
│   │   ├── repository/             Ranking, Stats, UserDailyStats, DailyAward repositories (grouped/native aggregate queries)
│   │   └── dto/                    RankingDTO, StatsDTO, PeladaAwardsDTO, PlayerPelada{Stats,History}DTO, UserMatchHistoryDTO, UserStatsTimelineDTO
│   ├── chat/                       ChatController (STOMP + @MessageExceptionHandler), ChatService (save message, paged history), Message, MessageRepository
│   │   └── dto/                    MessageDTO, SendMessageRequest
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
- **Denormalized aggregates.** `Ranking`, `Stats` and `UserDailyStats` are rebuilt from match data on finalize, results edits on a finalized daily, daily delete and pelada delete, through `AggregateRebuildService.rebuild(pelada, players)`. Any feature that changes match data must call it.

## Domain model

All entities use IDENTITY ids and **LAZY** fetching, Lombok `@Getter @Setter` (no `@Data`), `@ToString` with the id only, and id-based `equals`/`hashCode` that is safe with Hibernate proxies (`EntityIdentity`). `Daily.status` is the `DailyStatus` enum (`EnumType.STRING`, same DB values as before); `User.position` is a string validated by `UpdateProfileRequest`.

| Entity | Table | Fields / relations |
|--------|-------|--------------------|
| `User` | `users` | `email` (unique), `username` (unique case-insensitively in `AuthService`/`UserService`, not in the DB: production has legacy duplicates, so check with `existsByUsernameIgnoreCase…`, never a single-result find), `password` (BCrypt), `image`, `backgroundImage`, `stars` (default 3), `position` |
| `Pelada` | `peladas` | `name`, `dayOfWeek` (a `java.time.DayOfWeek` name, `MONDAY`..`SUNDAY`, validated on create/update), `timeOfDay`, `duration`, `address`, `reference`, `image`, `autoCreateDailyEnabled`, `numberOfTeams` (2), `playersPerTeam` (5), `createdAt`; `creator` → User; `members` M:N (`pelada_members`); `admins` M:N (`pelada_admins`) |
| `Daily` | `dailies` | `dailyDate`, `dailyTime`, `status` (`DailyStatus`, default `SCHEDULED`), `isFinished`, `championImage`, `createdAt`; `pelada`; `confirmedPlayers` M:N (`daily_confirmed_players`) |
| `Team` | `teams` | `name`, `color`; `daily`; `players` M:N (`team_players`) |
| `Match` | `matches` | `team1Score`, `team2Score`; `daily`, `team1`, `team2`, `winner` |
| `PlayerMatchStat` | `player_match_stats` | `goals`, `assists`; `match`, `user`. One row per player of the **two teams in that match** (`submitResults` and `populateFromMessage`), so `UserDailyStats.matchesPlayed` counts the matches a player actually played |
| `UserDailyStats` | `user_daily_stats` | `goals`, `assists`, `matchesPlayed`, `wins`, `wonSession`; `daily`, `user`. One row per player on the session's teams, written by finalize (and by `submitResults` on a FINISHED daily) |
| `LeagueTableEntry` | `league_table_entries` | `position`, `wins`, `draws`, `losses`, `goalsFor`, `goalsAgainst`, `points`; `daily`, `team` |
| `DailyAward` | `daily_awards` | `daily` (unique); `puskasWinners`, `wiltballWinners`, `artilheiroWinners`, `garcomWinners` (M:N `daily_award_*`) |
| `Ranking` | `rankings` (unique `pelada_id`+`user_id`) | `goals`, `assists`, `matchesPlayed`, `wins`; `pelada`, `user` |
| `Stats` | `stats` | `goals`, `assists`, `matchesPlayed`, `wins`, `sessionsPlayed`, `matchWins`, `puskasDates` (`stats_puskas_dates`); `user` (1:1) |
| `Message` | `messages` | `content` (≤ 500), `sentAt`; `pelada`, `sender` |

There are no JPA cascades: deletes are explicit in the services (`DailyService.deleteDailyData`, `PeladaService.deletePelada`).

**Daily status machine** (`DailyStatus.canTransitionTo`): `SCHEDULED → CONFIRMED | CANCELED`, `CONFIRMED → IN_COURSE | CANCELED`. `FINISHED` is reached only through finalize; populate can also move a daily to `IN_COURSE`. Attendance and team changes are blocked while the status is in `DailyStatus.LOCKED` (`IN_COURSE`, `FINISHED`, `CANCELED`).

**Positions** (`UpdateProfileRequest`, `@Pattern`): `GOALKEEPER`, `DEFENDER`, `MIDFIELDER`, `FORWARD`, `GOLEIRO`, `ZAGUEIRO`, `MEIO`, `ATACANTE`; an empty string clears it. The web app offers the Portuguese four.

**Awards:** Puskás (best goal), "Wiltball" (shown as "Bola Murcha" in the UI), Artilheiro (top scorer), Garçom (top assists).

New status-like fields must be Java enums stored with `@Enumerated(EnumType.STRING)`.

## Database schema & migrations

### How the schema is managed

- **Flyway 9** (`flyway-core`, version managed by Spring Boot 3.2; Postgres support is built into 9.x, so there is no `flyway-database-postgresql`) runs `src/main/resources/db/migration/V*.sql` at startup, before JPA.
- Hibernate runs with `ddl-auto=${DDL_AUTO:validate}`: it never changes the schema, only checks that the tables match the entities, and the app fails to start if they don't. `DDL_AUTO` is not set by `deploy.sh` or the compose files (the deploy fails if `.env` sets it); keep the default.
- `spring.flyway.baseline-on-migrate=true` + `baseline-version=1`: a database that already has tables but no `flyway_schema_history` (production, an old dev volume) is marked as V1 without running it, and only later versions run. An empty database runs V1 and builds the whole schema.
- `V1__baseline.sql` is the schema Hibernate generated from the entities on 2026-10-03 (PostgreSQL 15, `pg_dump --schema-only`), not a dump of production. Production's tables were built by `ddl-auto=update` from the same entities. Checked against a copy of production on 2026-10-04: same columns and constraints, except two leftover nullable columns `daily_awards.puskas_winner_id` / `wiltball_winner_id` (all null, FKs to `users`) that no entity maps; V4 drops them.
- Production runs PostgreSQL 17 (Supabase); Flyway 9.22 logs "PostgreSQL 17 is newer than this version of Flyway" but works. Upgrading means Flyway 10 + `flyway-database-postgresql`.
- Tests: H2 `create-drop` with Flyway disabled for the unit/integration tests; `FlywayMigrationTest` (Testcontainers, `postgres:17-alpine` like production, skipped without Docker) runs every migration on an empty Postgres and validates it against the entities; `MigrationDataTest` runs the data-changing migrations (V4, V5) against production-shaped rows.

| Version | What it does |
|---------|--------------|
| V1 | Baseline schema |
| V2 | Deletes `player_match_stats` rows of players on neither team of the match (keeps rows with goals/assists) and rebuilds `matches_played` in `user_daily_stats`, `rankings`, `stats` (destructive: back up first) |
| V3 | Indexes every FK and lookup column (`pelada_id`, `daily_id`, `user_id`, `team_id`, `match_id`, award join tables, `messages(pelada_id, sent_at)`); non-destructive |
| V4 | Drops the production-only legacy columns `daily_awards.puskas_winner_id` / `wiltball_winner_id`; aborts if any row has a value (destructive: back up first) |
| V5 | Converts Portuguese `peladas.day_of_week` values (`Segunda`, `Sabado`…) to `MONDAY`..`SUNDAY`; unknown values are left as they are |

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

- CSRF disabled, stateless sessions, JSON 401 entry point.
- Public: `/api/v1/auth/**`, `/api/v1/files/**`, `/ws/**` (the STOMP layer authenticates itself, see WebSocket). Everything else requires a valid token.
- `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`. An invalid/expired Bearer token returns 401 on **any** path, including the public ones.
- No roles: authorities are always empty, there is no `@PreAuthorize`/`@EnableMethodSecurity`. **All authorization is done in services** (see [Ownership](#ownership--authorization)).
- Passwords: BCrypt (strength 10).

### Tokens (`S/domain/auth/JwtService.java`)

- HS256 JWT, claims `sub` = email, `userId`, `email`, `iat`, `exp`; TTL 7 days by default.
- Returned in the body (`AuthResponseDTO {token, user}`); the frontend keeps it in `localStorage`.
- No refresh token and no revocation. The filter does not check that the user still exists.

### CORS

- One list, `ALLOWED_ORIGINS` (comma-separated, property `app.cors.allowed-origins`), read by `CorsConfig`. Its `CorsConfigurationSource` is applied by Spring Security (`http.cors`), and `WebSocketConfig` uses the same origins for `/ws`.
- Production sets it to `APP_URL` (`deploy.yml`); the dev default is `http://localhost:5173`.

### Rate limiting

`AuthRateLimitFilter` (in the security chain): 10 requests per minute per client IP on `POST /api/v1/auth/login` and `POST /api/v1/auth/register`, answering 429 with the usual error body. Buckets live in a Caffeine cache (max 100k entries, evicted 10 min after the last access). The IP is `getRemoteAddr()`, which reflects `X-Forwarded-For` because of `server.forward-headers-strategy=framework`. In memory: one API instance only. New rate limits go into a filter like this one.

### WebSocket (`S/domain/auth/JwtChannelInterceptor.java`)

- `CONNECT` needs `Authorization: Bearer <token>` in the STOMP headers; without a valid token the frame is rejected.
- `SUBSCRIBE` is allowed only to `/topic/pelada/{id}` when the user is a member of that pelada (`existsByIdAndMembers_Email`), and to the user's own `/user/queue/errors`. Any other destination is rejected.
- `SEND` needs an authenticated session and a destination under `/app/`; a frame sent straight to a broker prefix (`/topic`, `/queue`, `/user`) is rejected, so every message goes through `ChatController` → `ChatService`.
- Clients send to `/app/pelada/{peladaId}/send` (`SendMessageRequest {content}`); the server broadcasts `MessageDTO` to `/topic/pelada/{peladaId}`. Errors (`AppException` from `ChatService`) go back to the sender on `/user/queue/errors` as `{status, message, timestamp}`.

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
| Read pelada detail, dailies, daily detail, ranking, awards, member stats/history, chat history, send chat, subscribe to the chat topic | member (member stats/history: the target user must also be a member, else 404) |
| Update pelada, upload pelada image, add/remove player, set/unset admin | admin (the creator can't be removed or demoted; remove/set admin need the target to be a member, else 404) |
| Delete pelada (cascades dailies, results, teams, attendance, messages, rankings; rebuilds global Stats) | creator |
| Create daily, change status, delete daily, sort/swap teams, submit results, finalize, populate, champion image | admin of the daily's pelada |
| Confirm attendance (self) | member |
| Unconfirm (self) | must be confirmed |
| Confirm/unconfirm another player, confirm every member | admin, target must be a member |
| Rename team | member **and** player on that team (team must belong to the daily) |
| Change team color | member and (team player or admin) |
| Update profile, avatar, background | self |
| View a profile (`/users/{id}`) | any authenticated user; `email` only for self |
| User stats, timeline, match history (`/users/{id}/stats…`) | self, or a user who shares a pelada (403 otherwise); non-self viewers only get sessions of the peladas they share |
| Peladas in common (`/users/{id}/peladas`) | any; returns only peladas both users belong to |
| Search users | any; public fields only (`PublicUserDTO`), query ≥ 3 characters |

### Rules for new code

1. Resolve the caller from the token, never from the body or a path param. Ignore any `userId`/owner field the client sends for "who am I".
2. Every `{id}` route checks the relationship before doing anything else.
3. **Scope child ids to the parent in the path.** A `matchId`, `teamId` or `userId` in the body/path must be checked to belong to the `dailyId`/`peladaId` of the route (e.g. `team.getDaily().getId().equals(dailyId)`, as `updateTeamName` does). Otherwise an admin of one pelada can touch another pelada's data.
4. Use `PeladaAccessHelper` (exists queries) instead of loading `members`/`admins` collections just to call `contains`.
5. Don't expose email or other PII outside the pelada context; public user views return id, username, image, position, stars.
6. 403 for "exists but you can't", 404 for "doesn't exist". When even revealing existence matters, answer 404 for both.
7. Each new `{id}` route gets tests for: not a member (403), member but not admin (403 where admin is required), and a child id from another pelada/daily.

### Known gaps (fix when touching these areas)

| Gap | Where |
|-----|-------|
| No unique constraint on `user_daily_stats(daily_id, user_id)` / `player_match_stats(match_id, user_id)` (the code never writes duplicates, but the database doesn't enforce it). Check production for duplicates before adding them | `V1__baseline.sql` |
| The JWT is valid for 7 days with no revocation, and the filter doesn't check that the user still exists | `JwtAuthFilter`, `JwtService` |

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

### Files (`FileController`)
| Method | Path | Auth | Response |
|--------|------|------|----------|
| GET | `/files/{filename}` | public | the image (content type from extension) |

### Peladas (`PeladaController`, `/peladas`)
| Method | Path | Body | Auth | Response |
|--------|------|------|------|----------|
| POST | `/peladas` | `CreatePeladaRequestDTO` (`dayOfWeek` `MONDAY`..`SUNDAY`, `timeOfDay` `HH:mm`, 2–10 teams, 2–20 players per team) | any | 201 `PeladaResponseDTO` |
| GET | `/peladas/my` | — | self | `List<PeladaResponseDTO>` with `memberCount`, `isAdmin` (the caller administers it) and `nextDaily` (`{id, date, time, status, confirmedCount, capacity, isConfirmed}`: the next `SCHEDULED`/`CONFIRMED` session from today, or null; `capacity` = `numberOfTeams × playersPerTeam`, `isConfirmed` is the caller's attendance). Grouped queries, constant count |
| GET | `/peladas/{id}` | — | member | `PeladaDetailResponseDTO` |
| PUT | `/peladas/{id}` | `UpdatePeladaRequestDTO` (partial; `dayOfWeek` `MONDAY`..`SUNDAY`, `timeOfDay` `HH:mm`) | admin | `PeladaResponseDTO` |
| DELETE | `/peladas/{id}` | — | creator | 204; deletes dailies (results, teams, attendance, photos), messages and rankings, then rebuilds the players' global Stats |
| POST | `/peladas/{id}/players` | `AddPlayerRequestDTO {userId}` | admin | 200; 409 if already a member |
| DELETE | `/peladas/{id}/players/{userId}` | — | admin | 200; 404 if `userId` isn't a member |
| PUT | `/peladas/{id}/players/{userId}/admin` | `SetAdminRequestDTO {isAdmin}` | admin | 200; 404 if `userId` isn't a member |
| GET | `/peladas/{id}/ranking` | — | member | `List<RankingDTO>` |
| GET | `/peladas/{id}/members/{userId}/stats` | — | member (404 if `userId` isn't a member) | `PlayerPeladaStatsDTO` |
| GET | `/peladas/{id}/members/{userId}/history?limit=` | — | member (404 if `userId` isn't a member) | `PlayerPeladaHistoryDTO {userId, totalSessions, rows: [{dailyId, date, goals, assists, matchesPlayed, wins, wonSession}]}`, newest first, one row per finalized session the player took part in. `limit` (optional, 1–100, else 400) returns only the last N sessions; without it every session is returned. `totalSessions` is always the full count (the count query only runs when the page is full) |
| GET | `/peladas/{id}/awards` | — | member | `PeladaAwardsDTO` |
| GET | `/peladas/{id}/messages?page=0&size=50` | — | member | `List<MessageDTO>` (oldest first); `size` is clamped to 1–100, `page` to ≥ 0 |
| POST | `/peladas/{id}/image` | multipart `file` | admin | `PeladaResponseDTO` |

### Dailies (`DailyController`)
| Method | Path | Body | Auth | Response |
|--------|------|------|------|----------|
| POST | `/peladas/{peladaId}/dailies` | `CreateDailyRequestDTO` | admin | 201 `DailyListItemDTO` |
| GET | `/peladas/{peladaId}/dailies` | — | member | `List<DailyListItemDTO>` (`confirmedPlayerCount`, `teamCount`, `matchCount`, `championImage`, `isConfirmed` = the caller's attendance; grouped queries, constant count) |
| GET | `/dailies/{id}` | — | member | `DailyDetailDTO` (includes `isAdmin` for the caller and `peladaMembers`, every member's public fields, for the attendance list) |
| POST / DELETE | `/dailies/{id}/confirm` | — | member / confirmed | `DailyListItemDTO` |
| POST | `/dailies/{id}/confirm/all` | — | admin | `DailyListItemDTO`; confirms every member who hasn't confirmed (400 when the status is locked) |
| POST / DELETE | `/dailies/{id}/confirm/{userId}` | — | admin | `DailyListItemDTO` |
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

### Users (`UserController`, `/users`)
| Method | Path | Body | Auth | Response |
|--------|------|------|------|----------|
| GET | `/users/search?q=` | — | any | `List<PublicUserDTO {id, username, image, stars, position}>` (max 10); matches username or email; `q` needs ≥ 3 characters (400) |
| GET | `/users/{id}` | — | any | `ProfileDTO` (`email` only for self) |
| GET | `/users/{id}/peladas` | — | any | `List<PeladaResponseDTO>`: peladas the caller and `{id}` both belong to (`isAdmin`/`nextDaily.isConfirmed` refer to the caller) |
| PUT | `/users/{id}` | `UpdateProfileRequest {username 3–30, position, stars 1–5}` | self | `ProfileDTO` |
| POST | `/users/{id}/image`, `/users/{id}/background-image` | multipart `file` | self | `ProfileDTO` |
| GET | `/users/{id}/stats` | — | self or shares a pelada | `StatsDTO` |
| GET | `/users/{id}/stats/timeline?from&to` | — | self or shares a pelada | `UserStatsTimelineDTO` (other viewers: shared peladas only; `from` after `to` → 400) |
| GET | `/users/{id}/stats/matches` | — | self or shares a pelada | `UserMatchHistoryDTO` (other viewers: shared peladas only) |

When an endpoint or DTO changes, update this table, the frontend `src/api/*` + `src/types/*`, and `client/docs/FRONTEND.md` in the same change.

## Performance

- **Fetching:** everything is `LAZY`; keep it that way and load what a use case needs with `JOIN FETCH` or `@EntityGraph` (reference: `TeamRepository.findByDailyWithPlayers`, `PlayerMatchStatRepository.findByMatchInWithUser`). Never switch a relation to `EAGER`.
- **Read models:** `getMyPeladas` / `getSharedPeladas` use grouped member-count and next-session queries; `getDailiesForPelada` a grouped confirmed-count query; `getDailyDetail` fetches teams with players, matches with teams, stats with users and league entries with teams; ranking, chat and profile history use fetch joins; award counts are single grouped native queries (`DailyAwardRepository`). Keep new read paths at a constant number of queries.
- **Membership checks** use exists queries (`PeladaAccessHelper`), never `members.contains`.
- **Indexes:** every FK and lookup column is indexed (V3); index new ones in the same migration.
- **Aggregates:** compute in the DB with grouped queries (as finalize does: `aggregateStatsByUsers`), not by looping and querying per user.
- **Transactions:** `@Transactional` on writes, `@Transactional(readOnly = true)` on reads. `open-in-view` is on, so lazy loads in controllers silently work; don't rely on it — build DTOs inside the service transaction.
- **Pagination:** any list that grows with time (messages, dailies, match history) takes a `Pageable` with a capped `size` (chat: 1–100).
- **Single instance assumptions:** the in-memory STOMP broker, the rate-limit cache and `@Scheduled` (no ShedLock) only work with one API instance.
- **Scheduler:** `DailySchedulerService` runs hourly in server-local time; a failing pelada is logged and skipped. `dayOfWeek` is validated on create/update.

## Testing

Tests live in `src/test/java/com/futspring/backend`, in the same domain packages as the code they test (`domain/daily/DailyServiceTest`, `domain/stats/repository/UserDailyStatsRepositoryTest`…); shared test helpers (`BaseIntegrationTest`, `MembershipStubs`) are in `support/`, migration tests at the root.

- **Service unit tests** — JUnit 5 + Mockito + AssertJ, one class per service (`PeladaServiceTest`, `DailyServiceTest`, `DailyAttendanceServiceTest`…). Mock repositories and `UserAuthenticationHelper`; build a real `PeladaAccessHelper` over the mocked `PeladaRepository` and call `MembershipStubs.stubMembership(peladaRepository, pelada…)` so the exists queries answer from the entities' member/admin sets.
- **Controller integration tests** — extend `BaseIntegrationTest` (`@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureMockMvc`, `@Transactional`, real JWTs from `JwtService`).
- **Repository tests** — `@DataJpaTest` on H2 (`UserRepositoryTest`, `DailyRepositoryTest`, `UserDailyStatsRepositoryTest`, `DailyAwardRepositoryTest` for the native award queries).
- **WebSocket** — `JwtChannelInterceptorTest` covers CONNECT/SUBSCRIBE/SEND rules.
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
| 6 | Multi-line `"..."` string in `@Query` (doesn't compile on Java 17) or mismatched aliases | Text block `"""..."""`, one alias | — |
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
