# Conventions, patterns and antipatterns

> Code conventions, the patterns to copy and the numbered antipatterns table. Part of the [backend guide](README.md).

## Conventions

- Constructor injection with Lombok `@RequiredArgsConstructor`; no field `@Autowired`.
- `@Valid` on every `@RequestBody`; constraints (`@NotBlank`, `@Size`, `@Min`, `@NotNull`) on every request DTO field that has a rule. Nested lists need `@Valid` on the element type too (`List<@Valid MatchResultDTO>`).
- Read the principal with `@AuthenticationPrincipal String email` (every controller does).
- New enum-like fields are Java enums with `@Enumerated(EnumType.STRING)`.
- Explicit `@Table(name = ...)` and snake_case `@Column(name = ...)` on new entities/columns.
- DTOs: use records, record accessors (`request.name()`), and complete construction; keep existing Lombok `@Builder` and static mapping factories. No compatibility getters or post-construction setters. `StatsDTO` factories take award counts up front. Preserve validation on components and container elements, Portuguese messages, JSON names, and `@JsonInclude` rules. `CreatePeladaRequestDTO` uses a Jackson 3 builder with defaults of 2 teams and 5 players per team so omission keeps defaults while explicit invalid values still fail validation. Match score and imported player-stat request records also use DTO-local Jackson builders to keep omitted primitive inputs at zero while rejecting explicit nulls (Jackson 3 otherwise rejects missing primitive constructor components). Partial-update components stay nullable and have no defaults.
- Entities: prefer `@Getter @Setter` + `@NoArgsConstructor`/`@AllArgsConstructor`/`@Builder` over `@Data`, with `equals`/`hashCode` on the id only (see antipatterns).
- Logging with `@Slf4j`; no `System.out`/`printStackTrace`. No e-mail, token, password, Pix key or request body in a log message or argument: log ids.
- A new class goes where 'Where a class goes' (Package layout) says; don't add an `entity/`, `repository/`, `service/` or `event/` package.
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

Load the caller, load the resource, check the relationship, then act (see [The pattern](authorization.md#the-pattern)). Reference: `PeladaService.updatePelada`, `DailyTeamManagementService.updateTeamName` (also scopes the team to the daily), `DailyResultsService.submitResults` (scopes `matchId` to the session's matches, loaded once with `findByDaily`).

### 3. Map to DTO records with static factories

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

Don't introduce these. These are the general ones; the rest sit in the document of their subject. Numbers are shared by the whole backend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 1 | `@RequestBody` without `@Valid` (constraints in the DTO are never enforced: negative scores are accepted) | `@Valid @RequestBody`, constraints on the DTO | — |
| 6 | Multi-line `"..."` string in `@Query` (doesn't compile) or mismatched aliases | Text block `"""..."""`, one alias | — |
| 23 | Dead code left around | Delete it | — |

## Antipatterns by topic

Every antipattern of the backend, by number. Older records cite these numbers.

| # | Antipattern | Document |
|---|---|---|
| 1 | `@RequestBody` without `@Valid` (constraints in the DTO are never enforced: negative scores are accepted) | [conventions.md](conventions.md#antipatterns) |
| 2 | Trust a child id from the request without checking it belongs to the route's parent (IDOR) | [authorization.md](authorization.md#antipatterns) |
| 3 | Endpoints that read another user's data with no relationship check, or expose email | [authorization.md](authorization.md#antipatterns) |
| 4 | Unauthorized WebSocket subscriptions / anonymous CONNECT | [security.md](security.md#antipatterns) |
| 5 | Secrets in tracked or deployable files (DB password, JWT secret, Supabase URL) | [configuration.md](configuration.md#antipatterns) |
| 6 | Multi-line `"..."` string in `@Query` (doesn't compile) or mismatched aliases | [conventions.md](conventions.md#antipatterns) |
| 7 | Catch-all handler that swallows the exception without logging | [error-handling.md](error-handling.md#antipatterns) |
| 8 | Lombok `@Data` on entities (equals/hashCode over every field and lazy collections; `toString` triggers lazy loads) | [domain-model.md](domain-model.md#antipatterns) |
| 9 | Magic strings for status/position duplicated across services | [domain-model.md](domain-model.md#antipatterns) |
| 10 | Copy-pasted business logic | [structure.md](structure.md#antipatterns) |
| 11 | Business logic or several service calls in a controller | [structure.md](structure.md#antipatterns) |
| 12 | In-memory, never-evicted rate-limit map keyed by the proxy IP | [security.md](security.md#antipatterns) |
| 13 | Upload extension taken from the client filename; old image deleted before the transaction commits; replaced images never deleted | [security.md](security.md#antipatterns) |
| 14 | Free-text fields that code later parses (`DayOfWeek.valueOf`) | [domain-model.md](domain-model.md#antipatterns) |
| 15 | Deleting a parent that still has children (FK violation → 500) | [domain-model.md](domain-model.md#antipatterns) |
| 16 | `EAGER` fetches, per-row lazy loads in loops, loading member sets for `contains` | [performance.md](performance.md#antipatterns) |
| 17 | Unbounded lists / uncapped page size | [performance.md](performance.md#antipatterns) |
| 18 | Two CORS sources that disagree | [security.md](security.md#antipatterns) |
| 19 | Schema changes made only by editing entities and relying on `ddl-auto=update` in production | [migrations.md](migrations.md#antipatterns) |
| 20 | New `NOT NULL` column without a default/backfill, or a rename in one step | [migrations.md](migrations.md#antipatterns) |
| 21 | Editing a migration that already ran | [migrations.md](migrations.md#antipatterns) |
| 22 | Seeding known-password users whenever the profile isn't `prod` | [configuration.md](configuration.md#antipatterns) |
| 23 | Dead code left around | [conventions.md](conventions.md#antipatterns) |
