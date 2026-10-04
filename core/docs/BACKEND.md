# Backend Documentation — Futspring API

Spring Boot REST API in `backend/`. Base package: `com.futspring.backend` (`src/main/java/com/futspring/backend`, written `S/` below).

Futspring manages amateur soccer groups ("peladas"): members and admins, match-day sessions ("dailies") with attendance, team sorting, match results, league table, awards, a pelada-wide ranking, per-player stats and a live chat.

## Stack

- Java 17, Spring Boot 3.2.3, Maven (`./mvnw`)
- Spring Web MVC, Spring Data JPA (Hibernate), Spring Security, Bean Validation
- Spring WebSocket — STOMP over SockJS, in-memory simple broker (chat)
- PostgreSQL 15 (runtime, Supabase in production), H2 in PostgreSQL mode (tests)
- JJWT 0.11.5 (HS256), Bucket4j 8.7 (login rate limit, in memory), Lombok
- Tests: JUnit 5, Mockito, AssertJ, spring-security-test, Testcontainers 1.21 (overridden in `pom.xml`: Boot's 1.19 can't talk to Docker 29)
- Flyway 9 (schema migrations, see [Database schema & migrations](#database-schema--migrations))
- Not present: Swagger/springdoc, Actuator, Redis, caching, MapStruct/ModelMapper

## Commands

Run from `backend/`:

```bash
./mvnw spring-boot:run                           # needs DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET
./mvnw test                                      # all tests (H2, src/test/resources/application.properties)
./mvnw test -Dtest=PeladaServiceTest             # one test class
./mvnw clean package -DskipTests                 # build jar
docker compose up --build                        # API + postgres:15-alpine (docker-compose.yml, dev)
```

There is no CI. Run `./mvnw test` before finishing any backend change.

Deploy: `deploy.sh` (repo root, gitignored) builds the frontend and an amd64 Docker image, ships it over `scp` to the VPS and runs it on port 8081 with uploads mounted at `/opt/futspring-uploads`. Production uses `docker-compose.prod.yml` values from the environment.

## Configuration & environment

There is a single `src/main/resources/application.properties`; there are no `application-<profile>` files.

| Variable | Purpose | Default |
|----------|---------|---------|
| `DB_URL` | JDBC URL | `jdbc:postgresql://futspring-postgres-dev:5432/futspringdb` |
| `DB_USERNAME`, `DB_PASSWORD` | DB credentials | — (required) |
| `JWT_SECRET` | HS256 key; **must be ≥ 32 bytes** (`Keys.hmacShaKeyFor`) | — (required) |
| `JWT_EXPIRATION_MS` | access token TTL | `604800000` (7 days) |
| `DDL_AUTO` | `spring.jpa.hibernate.ddl-auto`; leave unset (the schema is owned by Flyway) | `validate` |
| `ALLOWED_ORIGINS` | WebSocket allowed origin pattern | `http://localhost:5173` |
| `SPRING_PROFILES_ACTIVE` | `prod` in production (disables the seed) | — |

- Uploads go to `app.uploads.dir=/app/uploads` (container path; override it when running outside Docker). Multipart limit is 10 MB, but `FileUploadService` enforces 5 MB.
- Hibernate: `show-sql=false`, `batch_size=50`, ordered inserts/updates. `spring.jpa.open-in-view` is not set, so it is **on** (default).
- `DataInitializer` (`@Profile("!prod")`) seeds an empty database with 20 users (password `senha123`; admin `leal@futspring.com`), the pelada "Pelada do Fut", 7 weekly finished dailies and 1 scheduled daily for today. Each finished daily gets teams (4 teams of 5, or 3 teams / 15 confirmed players in two of the weeks so histories differ), 6 matches with random results from a fixed seed (`new Random(2026)`), and is closed through `DailyResultsService.submitResults` + `finalizeDaily`, so match stats, league table, `UserDailyStats`, `Ranking`, `Stats` and awards follow the real rules. It also runs in `@SpringBootTest` contexts (no profile there). Production **must** run with `SPRING_PROFILES_ACTIVE=prod`.
- Test config (`src/test/resources/application.properties`): H2 PostgreSQL mode, `create-drop`, Flyway disabled, fixed test JWT secret, 1 h TTL, uploads in a temp dir.
- **Secrets:** all credentials come from the environment (`.env`, `.env.example` documents the keys). Never write a password, DB URL with credentials, or JWT secret into a tracked file, script, compose file or doc.

## Package layout

```
com.futspring.backend
├── FutSpringApplication        main class, @EnableScheduling
├── config/
│   ├── SecurityConfig          filter chain, BCrypt PasswordEncoder
│   ├── CorsConfig              MVC CORS mappings (hardcoded origins)
│   ├── JwtConfig               @Value holder for secret + TTL
│   ├── WebSocketConfig         STOMP endpoint /ws (SockJS), broker /topic, app prefix /app, JwtChannelInterceptor
│   └── DataInitializer         dev seed (CommandLineRunner, !prod)
├── controller/                 AuthController, PeladaController, DailyController, UserController, FileController, ChatController (STOMP)
├── dto/                        request/response DTOs (Lombok @Data/@Builder), response DTOs map themselves with static from(entity)
├── entity/                     12 JPA entities (see Domain model)
├── exception/                  AppException (RuntimeException + HttpStatus), ErrorResponse, GlobalExceptionHandler
├── filter/JwtAuthFilter        reads Authorization: Bearer, sets the email as principal
├── helper/UserAuthenticationHelper   getAuthenticatedUser(email) → User or 404
├── repository/                 Spring Data JPA interfaces (JPQL in Award, PlayerMatchStat, Team, UserDailyStats, User)
├── service/
│   ├── AuthService             register / login, issues JWT
│   ├── PeladaService           pelada CRUD, members, admins, user search, image
│   ├── DailyService            create, list, status transitions, delete, detail aggregation
│   ├── DailyAttendanceService  confirm / unconfirm (self and by admin)
│   ├── DailyTeamManagementService  sort teams (star-balanced LPT), swap, rename, color
│   ├── DailyResultsService     results, live league table, finalize (stats, ranking, awards), champion image, populate, clearResults
│   ├── DailyDTOMapper          builds TeamDTO
│   ├── DailySchedulerService   hourly cron that auto-creates the next daily
│   ├── RankingService, AwardsService, StatsService   read-side aggregates
│   ├── ChatService             save message, paged history
│   ├── FileUploadService       stores/deletes images on local disk (UUID names)
│   ├── JwtService              generate / validate / extract
│   └── UserService             profile get/update, avatar, background image
└── websocket/JwtChannelInterceptor   authenticates STOMP CONNECT
```

New code goes in the matching layer folder. When a service grows past ~300 lines or mixes responsibilities, split it by sub-domain the way the daily services are split (`DailyService` orchestrates, `DailyAttendanceService` / `DailyTeamManagementService` / `DailyResultsService` own one concern each).

## Architectural patterns

- **Controller → Service → Repository.** Controllers only read the principal, validate the body (`@Valid`) and call **one** service method. Authorization, business rules and mapping live in services.
- **DTOs only at the boundary.** Controllers never return entities. Response DTOs expose a static `from(entity)` factory (`PeladaResponseDTO.from`, `RankingDTO.from`…); aggregates that need several sources are assembled in the service or a dedicated mapper (`DailyDTOMapper`).
- **Domain errors** are thrown as `new AppException(HttpStatus.X, "mensagem")` and turned into `ErrorResponse` by `GlobalExceptionHandler`.
- **Caller resolution.** The principal is the email (`(String) authentication.getPrincipal()`); services load the user with `UserAuthenticationHelper.getAuthenticatedUser(email)`. Don't repeat `userRepository.findByEmail(...).orElseThrow(...)`.
- **Uploads** always go through `FileUploadService` (size and content-type checks, UUID names). Files are served publicly by `FileController` (`GET /api/v1/files/{filename}`, with a path-traversal guard).
- **Denormalized aggregates.** `Ranking`, `Stats` and `UserDailyStats` are rebuilt from match data on finalize and on daily delete. Any feature that changes match data must rebuild them the same way.

## Domain model

All entities use IDENTITY ids and **LAZY** fetching. Status and position are currently plain strings (no Java enums).

| Entity | Table | Fields / relations |
|--------|-------|--------------------|
| `User` | `users` | `email` (unique), `username`, `password` (BCrypt), `image`, `backgroundImage`, `stars` (default 3), `position` |
| `Pelada` | `peladas` | `name`, `dayOfWeek` (String, e.g. `SATURDAY`), `timeOfDay`, `duration`, `address`, `reference`, `image`, `autoCreateDailyEnabled`, `numberOfTeams` (2), `playersPerTeam` (5), `createdAt`; `creator` → User; `members` M:N (`pelada_members`); `admins` M:N (`pelada_admins`) |
| `Daily` | `dailies` | `dailyDate`, `dailyTime`, `status` (default `SCHEDULED`), `isFinished`, `championImage`, `createdAt`; `pelada`; `confirmedPlayers` M:N (`daily_confirmed_players`) |
| `Team` | `teams` | `name`, `color`; `daily`; `players` M:N (`team_players`) |
| `Match` | `matches` | `team1Score`, `team2Score`; `daily`, `team1`, `team2`, `winner` |
| `PlayerMatchStat` | `player_match_stats` | `goals`, `assists`; `match`, `user`. One row per player of the **two teams in that match** (`submitResults` and `populateFromMessage`), so `UserDailyStats.matchesPlayed` counts the matches a player actually played |
| `UserDailyStats` | `user_daily_stats` | `goals`, `assists`, `matchesPlayed`, `wins`, `wonSession`; `daily`, `user` |
| `LeagueTableEntry` | `league_table_entries` | `position`, `wins`, `draws`, `losses`, `goalsFor`, `goalsAgainst`, `points`; `daily`, `team` |
| `DailyAward` | `daily_awards` | `daily` (unique); `puskasWinners`, `wiltballWinners`, `artilheiroWinners`, `garcomWinners` (M:N `daily_award_*`) |
| `Ranking` | `rankings` (unique `pelada_id`+`user_id`) | `goals`, `assists`, `matchesPlayed`, `wins`; `pelada`, `user` |
| `Stats` | `stats` | `goals`, `assists`, `matchesPlayed`, `wins`, `sessionsPlayed`, `matchWins`, `puskasDates` (`stats_puskas_dates`); `user` (1:1) |
| `Message` | `messages` | `content` (≤ 500), `sentAt`; `pelada`, `sender` |

There are no cascades.

**Daily status machine** (`DailyService`): `SCHEDULED → CONFIRMED | CANCELED`, `CONFIRMED → IN_COURSE | CANCELED`. `FINISHED` is reached only through finalize; populate can also move a daily to `IN_COURSE`. Attendance and team changes are blocked while the status is `IN_COURSE`, `FINISHED` or `CANCELED`.

**Positions** (`UserService`): `GOALKEEPER`, `DEFENDER`, `MIDFIELDER`, `FORWARD`, `GOLEIRO`, `ZAGUEIRO`, `MEIO`, `ATACANTE`.

**Awards:** Puskás (best goal), "Wiltball" (shown as "Bola Murcha" in the UI), Artilheiro (top scorer), Garçom (top assists).

New status-like fields must be Java enums stored with `@Enumerated(EnumType.STRING)`; migrating the existing strings to enums is a planned cleanup.

## Database schema & migrations

### How the schema is managed

- **Flyway 9** (`flyway-core`, version managed by Spring Boot 3.2; Postgres support is built into 9.x, so there is no `flyway-database-postgresql`) runs `src/main/resources/db/migration/V*.sql` at startup, before JPA.
- Hibernate runs with `ddl-auto=${DDL_AUTO:validate}`: it never changes the schema, only checks that the tables match the entities, and the app fails to start if they don't. `DDL_AUTO` is no longer set by `deploy.sh` or the compose files; keep the default.
- `spring.flyway.baseline-on-migrate=true` + `baseline-version=1`: a database that already has tables but no `flyway_schema_history` (production, an old dev volume) is marked as V1 without running it, and only later versions run. An empty database runs V1 and builds the whole schema.
- `V1__baseline.sql` is the schema Hibernate generated from the entities on 2026-10-03 (PostgreSQL 15, `pg_dump --schema-only`), not a dump of production. Production's tables were built by `ddl-auto=update` from the same entities. Checked against a copy of production on 2026-10-04: same columns and constraints, except two leftover nullable columns `daily_awards.puskas_winner_id` / `wiltball_winner_id` (all null, FKs to `users`) that no entity maps; `validate` ignores them. Drop them in a later migration.
- Production runs PostgreSQL 17 (Supabase); Flyway 9.22 logs "PostgreSQL 17 is newer than this version of Flyway" but works. Upgrading means Flyway 10 + `flyway-database-postgresql`.
- Tests: H2 `create-drop` with Flyway disabled for the unit/integration tests; `FlywayMigrationTest` (Testcontainers, `postgres:17-alpine` like production, skipped without Docker) runs every migration on an empty Postgres and validates it against the entities.

| Version | What it does |
|---------|--------------|
| V1 | Baseline schema |
| V2 | Deletes `player_match_stats` rows of players on neither team of the match (keeps rows with goals/assists) and rebuilds `matches_played` in `user_daily_stats`, `rankings`, `stats` (destructive: back up first) |

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

### Filter chain (`S/config/SecurityConfig.java`)

- CSRF disabled, stateless sessions, JSON 401 entry point.
- Public: `/api/v1/auth/**`, `/api/v1/files/**`, `/ws/**`. Everything else requires a valid token.
- `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`. An invalid/expired Bearer token returns 401 on **any** path, including the public ones.
- No roles: authorities are always empty, there is no `@PreAuthorize`/`@EnableMethodSecurity`. **All authorization is done in services** (see [Ownership](#ownership--authorization)).
- Passwords: BCrypt (strength 10).

### Tokens (`S/service/JwtService.java`)

- HS256 JWT, claims `sub` = email, `userId`, `email`, `iat`, `exp`; TTL 7 days by default.
- Returned in the body (`AuthResponseDTO {token, user}`); the frontend keeps it in `localStorage`.
- No refresh token and no revocation. The filter does not check that the user still exists.

### CORS

- MVC: `S/config/CorsConfig.java:13` hardcodes `http://localhost:5173` and `https://futspring.luisgosampaio.com`. `SecurityConfig` doesn't call `http.cors()`, so the security chain doesn't apply it. Production works because frontend and API share an origin behind the proxy.
- WebSocket: `ALLOWED_ORIGINS` is passed as **one** pattern to `setAllowedOriginPatterns` (`WebSocketConfig.java:26`); a comma-separated list is not split.
- Target: one source of truth (`ALLOWED_ORIGINS`, split on commas) for both.

### Rate limiting

Only `POST /api/v1/auth/login`: 10 per minute per IP, Bucket4j buckets in an in-memory `ConcurrentHashMap` inside `AuthController` (`:28`) keyed by `getRemoteAddr()` (`:46`). Known limits: the map is never evicted, the proxy IP is used for everyone (no `server.forward-headers-strategy`), register is not limited, single instance only. New rate limits go into a filter, not a controller.

### WebSocket (`S/websocket/JwtChannelInterceptor.java`)

- The token is checked on `CONNECT` only, and a `CONNECT` without a token is still accepted (unauthenticated).
- `SUBSCRIBE` is **not** authorized (see known gaps).
- Clients send to `/app/pelada/{peladaId}/send` (`SendMessageRequest {content}`); the server broadcasts `MessageDTO` to `/topic/pelada/{peladaId}`.

### Uploads

`FileUploadService` checks size (5 MB) and the client-supplied content type (`image/jpeg`, `image/png`, `image/webp`). The stored extension comes from the original filename (`:33-35`) and is not whitelisted; derive it from the validated content type instead.

## Ownership & authorization

There are no roles; permissions come from the caller's relationship with the resource. **Every route that takes an id must check that relationship in the service.** The frontend's admin checks only hide UI.

### The pattern

```java
// DailyService.updateStatus
@Transactional
public DailyListItemDTO updateStatus(Long id, String newStatus, String currentUserEmail) {
    User caller = userAuthHelper.getAuthenticatedUser(currentUserEmail);    // 1. who is calling
    Daily daily = dailyRepository.findById(id)                              // 2. load the resource
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Daily not found"));
    Pelada pelada = daily.getPelada();                                      // 3. walk up to the pelada
    if (!pelada.getAdmins().contains(caller)) {                             // 4. check the relationship
        throw new AppException(HttpStatus.FORBIDDEN, "Only admins can update daily status");
    }
    ...                                                                     // 5. only then act
}
```

Relationships: **creator** (`pelada.creator`), **admin** (`pelada.admins`), **member** (`pelada.members`), **self** (`user.email == caller email`).

### Permission matrix

| Action | Required |
|--------|----------|
| Create pelada | any authenticated user (becomes creator, member and admin) |
| Read pelada detail, dailies, daily detail, ranking, awards, member stats/history, chat history, send chat | member (member stats/history: the target user must also be a member, else 404) |
| Update pelada, upload pelada image, add/remove player, set/unset admin | admin (the creator can't be removed or demoted) |
| Delete pelada | creator |
| Create daily, change status, delete daily, sort/swap teams, submit results, finalize, populate, champion image | admin of the daily's pelada |
| Confirm attendance (self) | member |
| Unconfirm (self) | must be confirmed |
| Confirm/unconfirm another player | admin, target must be a member |
| Rename team | member **and** player on that team (team must belong to the daily) |
| Change team color | member and (team player or admin) |
| Update profile, avatar, background | self |

### Rules for new code

1. Resolve the caller from the token, never from the body or a path param. Ignore any `userId`/owner field the client sends for "who am I".
2. Every `{id}` route checks the relationship before doing anything else.
3. **Scope child ids to the parent in the path.** A `matchId`, `teamId` or `userId` in the body/path must be checked to belong to the `dailyId`/`peladaId` of the route (e.g. `team.getDaily().getId().equals(dailyId)`, as `updateTeamName` does). Otherwise an admin of one pelada can touch another pelada's data.
4. Prefer `exists` queries (`existsByIdAndMembers_Email`) over loading `members`/`admins` collections just to call `contains`.
5. Don't expose email or other PII outside the pelada context; public user views return id, username, image, position, stars.
6. 403 for "exists but you can't", 404 for "doesn't exist". When even revealing existence matters, answer 404 for both.
7. Each new `{id}` route gets tests for: not a member (403), member but not admin (403 where admin is required), and a child id from another pelada/daily.

### Known gaps (fix when touching these areas)

| Gap | Where |
|-----|-------|
| `submitResults` loads `matchRepository.findById(result.getMatchId())` without checking the match belongs to the daily → an admin can overwrite another pelada's match and delete its stats (IDOR) | `S/service/DailyResultsService.java:68` |
| STOMP `SUBSCRIBE` isn't authorized: anyone can subscribe to `/topic/pelada/{id}` and read the chat; an unauthenticated `SEND` NPEs on `principal.getName()` | `JwtChannelInterceptor`, `ChatController` |
| `/users/{id}/stats`, `/stats/timeline`, `/stats/matches` have no relationship check (caller email unused) | `S/service/StatsService.java` |
| `/users/search` (no minimum query length) and `/users/{id}` return emails | `UserResponseDTO`, `ProfileDTO` |

## Error handling (`S/exception/GlobalExceptionHandler.java`)

| Exception | Status | Body |
|-----------|--------|------|
| `MethodArgumentNotValidException` | 400 | `{status, message: "Validation failed", timestamp, errors: {field: message}}` |
| `AppException` | its status | `ErrorResponse {status, message, timestamp}` |
| any other `Exception` | 500 | `ErrorResponse` with "An unexpected error occurred" — **not logged** |

The 401s from the security entry point and `JwtAuthFilter` are hand-built maps, so there are three shapes in total. The frontend reads `message` (and `errors` for validation).

Not handled yet (become 500): malformed JSON (`HttpMessageNotReadableException`), missing/mistyped params, `MaxUploadSizeExceededException`, `DataIntegrityViolationException` (e.g. deleting a pelada that still has dailies/messages/rankings: no cascade).

Rules:
- Throw `AppException` for every expected failure (message in Portuguese for new code, see Conventions); never let a `NullPointerException` or FK violation be the error.
- Add handlers to `GlobalExceptionHandler` for framework exceptions (400/413/409) instead of try/catch in services.
- The catch-all must log the exception (`log.error("Unhandled", ex)`) — add `@Slf4j`.

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
| POST | `/peladas` | `CreatePeladaRequestDTO` | any | 201 `PeladaResponseDTO` |
| GET | `/peladas/my` | — | self | `List<PeladaResponseDTO>` |
| GET | `/peladas/{id}` | — | member | `PeladaDetailResponseDTO` |
| PUT | `/peladas/{id}` | `UpdatePeladaRequestDTO` | admin | `PeladaResponseDTO` |
| DELETE | `/peladas/{id}` | — | creator | 204 |
| POST | `/peladas/{id}/players` | `AddPlayerRequestDTO {userId}` | admin | 200 |
| DELETE | `/peladas/{id}/players/{userId}` | — | admin | 200 |
| PUT | `/peladas/{id}/players/{userId}/admin` | `SetAdminRequestDTO {isAdmin}` | admin | 200 |
| GET | `/peladas/{id}/ranking` | — | member | `List<RankingDTO>` |
| GET | `/peladas/{id}/members/{userId}/stats` | — | member (404 if `userId` isn't a member) | `PlayerPeladaStatsDTO` |
| GET | `/peladas/{id}/members/{userId}/history?limit=` | — | member (404 if `userId` isn't a member) | `PlayerPeladaHistoryDTO {userId, totalSessions, rows: [{dailyId, date, goals, assists, matchesPlayed, wins, wonSession}]}`, newest first, one row per finalized session the player took part in. `limit` (optional, 1–100, else 400) returns only the last N sessions; without it every session is returned. `totalSessions` is always the full count (the count query only runs when the page is full) |
| GET | `/peladas/{id}/awards` | — | member | `PeladaAwardsDTO` |
| GET | `/peladas/{id}/messages?page=0&size=50` | — | member | `List<MessageDTO>` (oldest first) |
| POST | `/peladas/{id}/image` | multipart `file` | admin | `PeladaResponseDTO` |

### Dailies (`DailyController`)
| Method | Path | Body | Auth | Response |
|--------|------|------|------|----------|
| POST | `/peladas/{peladaId}/dailies` | `CreateDailyRequestDTO` | admin | 201 `DailyListItemDTO` |
| GET | `/peladas/{peladaId}/dailies` | — | member | `List<DailyListItemDTO>` |
| GET | `/dailies/{id}` | — | member | `DailyDetailDTO` (includes `isAdmin` for the caller) |
| POST / DELETE | `/dailies/{id}/confirm` | — | member / confirmed | `DailyListItemDTO` |
| POST / DELETE | `/dailies/{id}/confirm/{userId}` | — | admin | `DailyListItemDTO` |
| POST | `/dailies/{id}/sort-teams` | — | admin | `List<TeamDTO>` |
| PUT | `/dailies/{id}/teams/swap` | `SwapPlayersRequestDTO` | admin | `List<TeamDTO>` |
| PATCH | `/dailies/{dailyId}/teams/{teamId}/name` | `UpdateTeamNameRequestDTO` | team player | `TeamDTO` |
| PATCH | `/dailies/{dailyId}/teams/{teamId}/color` | `UpdateTeamColorRequestDTO` | team player or admin | `TeamDTO` |
| PUT | `/dailies/{id}/status` | `UpdateDailyStatusRequestDTO` | admin | `DailyListItemDTO` |
| POST | `/dailies/{id}/results` | `List<MatchResultDTO>` | admin | `List<MatchDTO>` |
| POST | `/dailies/{id}/finalize` | `FinalizeDailyRequestDTO {puskasWinnerIds, wiltballWinnerIds}` | admin | `DailyDetailDTO` |
| POST | `/dailies/{id}/populate` | `PopulateDailyRequestDTO` | admin | `DailyDetailDTO` |
| PUT | `/dailies/{id}/champion-image` | multipart `file` | admin | `DailyListItemDTO` |
| DELETE | `/dailies/{id}` | — | admin | 204 |

### Users (`UserController`, `/users`)
| Method | Path | Body | Auth | Response |
|--------|------|------|------|----------|
| GET | `/users/search?q=` | — | any | `List<UserResponseDTO>` (max 10) |
| GET | `/users/{id}` | — | any | `ProfileDTO` |
| PUT | `/users/{id}` | `UpdateProfileRequest {username 3–30, position, stars 1–5}` | self | `ProfileDTO` |
| POST | `/users/{id}/image`, `/users/{id}/background-image` | multipart `file` | self | `ProfileDTO` |
| GET | `/users/{id}/stats` | — | any | `StatsDTO` |
| GET | `/users/{id}/stats/timeline?from&to` | — | any | `UserStatsTimelineDTO` |
| GET | `/users/{id}/stats/matches` | — | any | `UserMatchHistoryDTO` |

When an endpoint or DTO changes, update this table, the frontend `src/api/*` + `src/types/*`, and `frontend/documentation/FRONTEND.md` in the same change.

## Performance

- **Fetching:** everything is `LAZY`; keep it that way and load what a use case needs with `JOIN FETCH` or `@EntityGraph` (reference: `TeamRepository.findByDailyWithPlayers`, `PlayerMatchStatRepository.findByMatchInWithUser`). Never switch a relation to `EAGER`.
- **Known N+1 hot spots:** `getMyPeladas` (`members.size()` per pelada), `getDailiesForPelada` (`confirmedPlayers.size()` per daily), `DailyService.getDailyDetail` (teams, matches, league entries, user stats, awards), `RankingService.getRanking` (user per row), `AwardsService.getAwards` (4 collections per award), `StatsService` timeline/history (daily and pelada per row). Use count queries/projections or fetch joins when touching them.
- **Membership checks** load whole `members`/`admins` sets; prefer `exists` queries.
- **Aggregates:** compute in the DB with grouped queries (as finalize does: `aggregateStatsByUsers`), not by looping and querying per user.
- **Transactions:** `@Transactional` on writes, `@Transactional(readOnly = true)` on reads. `open-in-view` is on, so lazy loads in controllers silently work; don't rely on it — build DTOs inside the service transaction.
- **Pagination:** any list that grows with time (messages, dailies, match history) takes a `Pageable` with a capped `size`. Chat history today accepts any `size`.
- **Single instance assumptions:** the in-memory STOMP broker, the login rate-limit map and `@Scheduled` (no ShedLock) only work with one API instance.
- **Scheduler:** `DailySchedulerService` runs hourly in server-local time. One bad pelada must not abort the run (catch per pelada and log); `dayOfWeek` must be validated on create/update.

## Testing

Tests live in `src/test/java/com/futspring/backend`, mirroring the main packages.

- **Service unit tests** — JUnit 5 + Mockito + AssertJ, one class per service (`PeladaServiceTest`, `DailyServiceTest`, `DailyAttendanceServiceTest`…). Mock repositories and `UserAuthenticationHelper`.
- **Controller integration tests** — extend `BaseIntegrationTest` (`@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureMockMvc`, `@Transactional`, real JWTs from `JwtService`).
- **Repository tests** — `@DataJpaTest` on H2 (`UserRepositoryTest`, `DailyRepositoryTest`, `UserDailyStatsRepositoryTest`).
- **Migration test** — `FlywayMigrationTest` applies every migration to a Postgres container and starts the app with `validate`. A new migration or entity change must keep it green (needs Docker).
- When adding an endpoint: a controller test (happy path, validation 400, 403 for non-member/non-admin, 404) and service tests for the rules, including the ownership cases listed in [Rules for new code](#rules-for-new-code).
- Mockito strict stubs are on: remove stubs a test doesn't use (`UnnecessaryStubbingException`).
- The last recorded run had failures (`FutSpringApplicationTests`, `DailySchedulerServiceTest`, `AwardsServiceTest`, unnecessary stubbings in `DailyResultsServiceTest`/`DailyTeamManagementServiceTest`). Don't add new failures; fix existing ones when touching those services.

## Conventions

- Constructor injection with Lombok `@RequiredArgsConstructor`; no field `@Autowired`.
- `@Valid` on every `@RequestBody`; constraints (`@NotBlank`, `@Size`, `@Min`, `@NotNull`) on every request DTO field that has a rule. Nested lists need `@Valid` on the element type too (`List<@Valid MatchResultDTO>`).
- Read the principal the same way everywhere; prefer `@AuthenticationPrincipal String email` in new handlers over casting `authentication.getPrincipal()`.
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
                                         Authentication authentication) {
    String email = (String) authentication.getPrincipal();
    return ResponseEntity.ok(userService.updateProfile(id, request, email));
}
```

Reference: `UserController`, `PeladaController.createPelada`.

### 2. Authorization in the service, before any change

Load the caller, load the resource, check the relationship, then act (see [The pattern](#the-pattern)). Reference: `PeladaService.updatePelada`, `DailyTeamManagementService.updateTeamName` (also scopes the team to the daily).

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

Caller lookup through `UserAuthenticationHelper`, uploads through `FileUploadService`, team mapping through `DailyDTOMapper`.

## Antipatterns

Don't introduce these. Some exist already ("Found in"); fix them when the change is small, otherwise mention them to the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 1 | `@RequestBody` without `@Valid` (constraints in the DTO are never enforced: negative scores are accepted) | `@Valid @RequestBody`, constraints on the DTO | `DailyController.java:95,105,115,125,157,168,178`, `PeladaController.java:98` |
| 2 | Trust a child id from the request without checking it belongs to the route's parent (IDOR) | Scope it: `match.getDaily().getId().equals(dailyId)` or a scoped query | `DailyResultsService.java:68` |
| 3 | Endpoints that read another user's data with no relationship check, or expose email | Check the relationship; return a public DTO without PII | `StatsService`, `UserResponseDTO`, `ProfileDTO` |
| 4 | Unauthorized WebSocket subscriptions / anonymous CONNECT | Authorize `SUBSCRIBE` to `/topic/pelada/{id}` for members only; reject CONNECT without a valid token | `JwtChannelInterceptor.java`, `ChatController.java` |
| 5 | Secrets in tracked or deployable files (DB password, JWT secret, Supabase URL) | Environment variables / `.env` (gitignored); rotate anything that leaked | `deploy.sh` (gitignored but plaintext), `docker-compose.yml` (dev values) |
| 6 | Multi-line `"..."` string in `@Query` (doesn't compile on Java 17) or mismatched aliases | Text block `"""..."""`, one alias | — |
| 7 | Catch-all handler that swallows the exception without logging | `log.error` in the catch-all; specific handlers for framework exceptions | `GlobalExceptionHandler.java:41` |
| 8 | Lombok `@Data` on entities (equals/hashCode over every field and lazy collections; `toString` triggers lazy loads) | `@Getter @Setter`, id-based equals/hashCode, no collection in `toString` | all of `S/entity/` |
| 9 | Magic strings for status/position duplicated across services | Java enums (`DailyStatus`, `Position`) with `EnumType.STRING`, one shared set of locked statuses | `LOCKED_STATUSES` in `DailyAttendanceService.java:26` and `DailyTeamManagementService.java:32` |
| 10 | Copy-pasted business logic | Extract a method/service | Ranking/Stats rebuild in `DailyResultsService` finalize vs `clearResults`; team wipe in `sortTeams`/`populateFromMessage`/`clearTeams`; `PlayerDTO` mapping in `DailyService` and `DailyDTOMapper` |
| 11 | Business logic or several service calls in a controller | One service method; filters for cross-cutting concerns | rate limiter in `AuthController.java:28-50`; `DailyController` finalize/populate call two services |
| 12 | In-memory, never-evicted rate-limit map keyed by the proxy IP | A filter with an evicting cache (Caffeine) and the real client IP (`server.forward-headers-strategy=framework`) | `AuthController.java:28,46` |
| 13 | Upload extension taken from the client filename; old image deleted before the transaction commits; replaced images never deleted | Extension from the validated content type; delete after commit (`TransactionSynchronization`); delete the previous file | `FileUploadService.java:33-35`, `PeladaService`, `UserService`, `DailyResultsService` (champion image) |
| 14 | Free-text fields that code later parses (`DayOfWeek.valueOf`) | Validate on input (enum or `@Pattern`); isolate failures per item in jobs | `DailySchedulerService.java:54`, `CreatePeladaRequestDTO`/`UpdatePeladaRequestDTO` |
| 15 | Deleting a parent that still has children (FK violation → 500) | Delete or cascade children explicitly in the service, or forbid with 409 | `PeladaService.deletePelada` |
| 16 | `EAGER` fetches, per-row lazy loads in loops, loading member sets for `contains` | Fetch joins, count/exists queries, projections | see [Performance](#performance) |
| 17 | Unbounded lists / uncapped page size | `Pageable` with a max size | `/peladas/{id}/messages` |
| 18 | Two CORS sources that disagree | One `ALLOWED_ORIGINS` list used by MVC, Security (`http.cors()`) and WebSocket | `CorsConfig.java:13`, `WebSocketConfig.java:26` |
| 19 | Schema changes made only by editing entities and relying on `ddl-auto=update` in production | Flyway migration in the same PR; `validate` in prod | — |
| 20 | New `NOT NULL` column without a default/backfill, or a rename in one step | Expand/contract (see [Migration rules](#migration-rules)) | — |
| 21 | Editing a migration that already ran | New migration | — |
| 22 | Seeding known-password users whenever the profile isn't `prod` | Seed only with an explicit `dev` profile | `DataInitializer.java:23,43` |
| 23 | Dead code left around | Delete it | `JwtService.extractUserId`, `DailyAwardRepository.findPuskasDatesByUser`/`countByPuskasWinnersContaining`, unused per-user `sum*`/`count*` queries in `UserDailyStatsRepository` |
