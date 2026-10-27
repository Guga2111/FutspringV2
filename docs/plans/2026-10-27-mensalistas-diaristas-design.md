# Mensalistas & diaristas — design

Date: 2026-10-27 · Status: approved · Branch: `feat/mensalistas`

## Goal

Each pelada distinguishes **mensalistas** (monthly members, guaranteed spot) from **diaristas** (pay per session). The admin sets how many mensalistas the pelada has and who they are. Mensalistas have priority on attendance: diaristas can only confirm themselves from 24 h before the session, and a mensalista who confirms into a full session takes the spot of the last diarista who joined.

## Business rules

### Membership type

1. `maxMensalistas` is a pelada field, edited in "Editar pelada", default 0, `0 ≤ maxMensalistas ≤ capacity` (`capacity = numberOfTeams × playersPerTeam`).
2. A new member is a diarista. This includes the creator and admins: being admin doesn't make anyone a mensalista.
3. Only admins mark/unmark a mensalista, from the member's ⋯ menu (next to "Definir estrelas"). Marking beyond the limit → 409 "Limite de mensalistas atingido (8/8)". Marking someone who already is one is idempotent.
4. Lowering the limit below the current count → 409 ("Desmarque 2 mensalistas antes de reduzir o limite"). The system never chooses who loses the status. Changing `numberOfTeams`/`playersPerTeam` so that capacity < `maxMensalistas` → 409 too.
5. The type is evaluated **at confirmation time** (and, for the bump victim, at bump time). No history of the type is kept.
6. A member removed from the pelada loses the status (row deleted, as with stars).

### Attendance

| Who confirms | Mensalista | Diarista |
|---|---|---|
| The player themselves | any time (while the session isn't locked) | only from **24 h before** the session start |
| Admin (single or "Confirmar todos") | any time | any time |

- **Session start** = `dailyDate` + `dailyTime` in the **pelada's time zone** (`peladas.time_zone`, IANA id). The window opens at `start − 24h`.
- **The time check is server-side only.** `POST /dailies/{id}/confirm` has no body; the server compares `Instant.now(clock)` with the start computed from the stored data. Nothing time-related is read from the request. `confirmedAt` is set by the server.
- **Capacity:** self-confirmation is refused with 409 "Sessão lotada" when `confirmed ≥ capacity` — except for mensalistas (below).
- **Admins bypass window and capacity** and can push a session over the limit. An admin confirming anyone (mensalista included) never bumps anyone.
- **Mensalista into a full session** (self-confirmation, any time): the diarista with the most recent `confirmedAt` is removed (tie-break: highest `user_id`) and the mensalista is confirmed. A diarista confirmed by an admin can be bumped like any other — the admin's confirmation only skips the window; a guaranteed spot means making the player a mensalista. If no diarista is confirmed, the mensalista joins over capacity.
- **No waiting list.** When someone withdraws, nobody is promoted; a bumped diarista may confirm again under the normal rules.
- **Bump notification:** the bump is recorded (`daily_attendance_bumps`) and the diarista is told by an in-app alert (until they dismiss it) **and** by e-mail (async, after commit; a Resend failure is only logged and never affects the confirmation). Every member of the pelada sees the session's bump history.

## Data model — migration `V9__mensalistas_and_attendance_times.sql` (non-destructive)

```sql
ALTER TABLE peladas ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'America/Sao_Paulo';
ALTER TABLE peladas ADD COLUMN max_mensalistas INTEGER NOT NULL DEFAULT 0 CHECK (max_mensalistas >= 0);

CREATE TABLE pelada_mensalistas (
    id         BIGSERIAL PRIMARY KEY,
    pelada_id  BIGINT NOT NULL REFERENCES peladas(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_pelada_mensalistas UNIQUE (pelada_id, user_id)
);
CREATE INDEX idx_pelada_mensalistas_user_id ON pelada_mensalistas (user_id);

-- table and PK (daily_id, user_id) unchanged
ALTER TABLE daily_confirmed_players ADD COLUMN confirmed_at TIMESTAMPTZ;
UPDATE daily_confirmed_players c SET confirmed_at = COALESCE(d.created_at, now())
  FROM dailies d WHERE d.id = c.daily_id;
ALTER TABLE daily_confirmed_players ALTER COLUMN confirmed_at SET NOT NULL;
ALTER TABLE daily_confirmed_players ALTER COLUMN confirmed_at SET DEFAULT now();

CREATE TABLE daily_attendance_bumps (
    id           BIGSERIAL PRIMARY KEY,
    daily_id     BIGINT NOT NULL REFERENCES dailies(id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL REFERENCES users(id)   ON DELETE CASCADE, -- bumped diarista
    bumped_by_id BIGINT NOT NULL REFERENCES users(id)   ON DELETE CASCADE, -- mensalista
    bumped_at    TIMESTAMPTZ NOT NULL,
    seen_at      TIMESTAMPTZ
);
CREATE INDEX idx_daily_attendance_bumps_daily_id ON daily_attendance_bumps (daily_id);
CREATE INDEX idx_daily_attendance_bumps_user_unseen ON daily_attendance_bumps (user_id) WHERE seen_at IS NULL;
```

| Entity | Change |
|---|---|
| `Pelada` | + `timeZone` (validated with `ZoneId.of`), + `maxMensalistas` |
| `PeladaMensalista` (new, `domain/pelada/`) | `pelada`, `user`, `createdAt`; shaped like `PeladaPlayerStars`. Having a row = mensalista |
| `DailyAttendance` (new, `domain/daily/entity/`) | maps `daily_confirmed_players` with `@EmbeddedId (dailyId, userId)` + `confirmedAt` (`Instant`) |
| `Daily` | the `confirmedPlayers` `@ManyToMany` is **removed**; its users (8 backend files: sort, counts, populate, confirm all, assembler…) go through `DailyAttendanceRepository` |
| `DailyAttendanceBump` (new) | `daily`, `user`, `bumpedBy`, `bumpedAt`, `seenAt` |

Notes:
- New time columns are `TIMESTAMPTZ` / `Instant` (absolute instants, independent of the server zone).
- `ON DELETE CASCADE` is a safety net only; the services keep deleting explicitly (`deletePelada`, `deleteDailyData`, `removePlayer`).
- New columns have defaults, so the old API version keeps inserting during a deploy.
- Legacy attendance rows get `confirmed_at = dailies.created_at`; only future open sessions use it.
- Decisions: one `pelada_mensalistas` table (rejected: a column on `pelada_player_stars`, or turning `pelada_members` into an entity). Bumps in their own table (rejected: a `bumped_at` column on attendance, which would force a `bumped_at IS NULL` filter on every confirmed-players query).

## Service rules

### Time

- A `Clock` bean (`Clock.systemUTC()`) in `shared/config`, injected; `Clock.fixed` in tests.
- `AttendanceWindow` (`domain/daily/`): `startsAt(daily) = ZonedDateTime.of(dailyDate, LocalTime.parse(dailyTime), ZoneId.of(pelada.timeZone)).toInstant()`, `diaristaOpensAt(daily) = startsAt − 24h`.

### `DailyAttendanceService.confirmAttendance` (self)

```
1. caller; daily = dailyRepository.findByIdForUpdate(id)        -- PESSIMISTIC_WRITE on the daily row
2. requireMember, requireUnlocked, already confirmed → 409 (as today)
3. mensalista = pelada_mensalistas exists (pelada, caller)
4. diarista and now < diaristaOpensAt → 403 "Diaristas podem confirmar a partir de sáb, 31/10 às 20:00" (pelada zone)
5. capacity = numberOfTeams × playersPerTeam; confirmed = count(daily)
6. confirmed < capacity → insert (confirmedAt = now(clock)); done
7. diarista → 409 "Sessão lotada"
8. mensalista, full:
     victim = confirmed player NOT in pelada_mensalistas, ORDER BY confirmed_at DESC, user_id DESC LIMIT 1
     victim → delete their attendance, save DailyAttendanceBump, publish DiaristaBumpedEvent(bumpId)
     insert the mensalista (over capacity when there was no victim)
```

- The row lock serializes confirmations of the same session (count and victim choice never race); different sessions don't block each other. It is a database lock, not a JVM lock: it holds until commit and works across API instances.
- `adminConfirmAttendance` / `adminConfirmAll` take the same lock; no window, no capacity, no bump.
- `disconfirmAttendance` / `adminDisconfirmAttendance` unchanged (delete the row).
- `DiaristaBumpedEmailListener` (`@Async @TransactionalEventListener`, after commit) sends `email/diarista-bumped.html` (same structure as `password-reset.html`, username HTML-escaped, link to `/daily/{id}`).

### `PeladaService`

- `setMensalista(peladaId, userId, isMensalista, email)`: admin; target must be a member (404); locks the pelada row (`findByIdForUpdate`) before counting so two admins can't exceed the limit; 409 when full; idempotent.
- `createPelada` / `updatePelada`: `timeZone` (invalid → 400, missing on create → `America/Sao_Paulo`), `maxMensalistas` with the 409 rules above.
- `removePlayer` / `deletePelada` delete `pelada_mensalistas` rows; `DailyService.deleteDailyData` deletes the session's bumps.

## API

New routes:

| Method | Path | Body | Auth | Response |
|---|---|---|---|---|
| PUT | `/peladas/{id}/players/{userId}/mensalista` | `SetMensalistaRequestDTO {isMensalista @NotNull}` | admin; target must be a member (404) | `PeladaMemberDTO`; 409 when the limit is full |
| POST | `/dailies/{id}/bumps/seen` | — | member; marks only the caller's bumps in that session | 204 |

Changed behaviour: `POST /dailies/{id}/confirm` (still no body) adds 403 (window closed) and 409 "Sessão lotada"; admin confirm routes ignore window and capacity; `POST /peladas` and `PUT /peladas/{id}` accept `timeZone` and `maxMensalistas`.

| DTO | New fields |
|---|---|
| `CreatePeladaRequestDTO` | `timeZone` (optional), `maxMensalistas` (optional, `@Min(0)`, default 0) |
| `UpdatePeladaRequestDTO` | `timeZone`, `maxMensalistas` (partial) |
| `PeladaMemberDTO` | `isMensalista` |
| `PeladaDetailResponseDTO` | `timeZone`, `maxMensalistas` |
| `PeladaResponseDTO.nextDaily` | `startsAt`, `confirmOpensAt`, `bump` (`capacity` already exists) |
| `DailyListItemDTO` | `capacity`, `startsAt`, `confirmOpensAt` |
| `DailyDetailDTO` | `capacity`, `startsAt`, `confirmOpensAt`, `myBump`, `bumps[]`; `peladaMembers[].isMensalista`; confirmed players get `confirmedAt` |

- All instants are ISO-8601 UTC strings; the frontend formats them in the pelada's zone.
- `startsAt`: session start in the pelada zone. `confirmOpensAt`: from when **the caller** may self-confirm; `null` = no window (mensalista). Only used by the UI; the server re-checks everything.
- `myBump` / `nextDaily.bump`: `{bumpedAt, bumpedBy: PublicUserDTO}` for the caller's unseen bump in that session, or `null`. `bumps[]`: `{user, bumpedBy, bumpedAt}` for the session, public fields only.
- Read paths keep a constant number of queries (mensalista ids per pelada in one query, unseen bumps grouped per session for `/peladas/my`).
- Error messages (pt-BR): "Diaristas podem confirmar a partir de sáb, 31/10 às 20:00", "Sessão lotada", "Limite de mensalistas atingido (8/8)", "Desmarque 2 mensalistas antes de reduzir o limite", "O limite de mensalistas não pode passar das vagas (10)".

## Frontend

- `types/pelada.ts`, `types/daily.ts`: the new fields. `api/peladas.ts`: `setMensalista`; `api/dailies.ts`: `markBumpsSeen`. `usePeladaActions.setMemberMensalista` merges the returned member.
- Pure helpers with tests: `utils/attendance.ts` `getSelfConfirmState({isConfirmed, isMensalista, confirmOpensAt, confirmedCount, capacity}, now)` → `open | notYet | full | priority`, the single source for the confirm button; `utils/dates.ts` `formatInZone(iso, timeZone)`; `utils/memberFilters.ts` type filter and counts.
- `useNowUntil(confirmOpensAt)`: a `setTimeout` to the opening instant so the button enables itself (no polling).
- `Badge` variant `membership` in `badge-variants.ts`: "Mensalista" (`info` / `info-muted`), "Diarista" (neutral outline); both themes.

| Screen | Change |
|---|---|
| `MembersGrid` / `MemberCard` | tag; "Mensalistas (n)" / "Diaristas (n)" chips next to the position chips; admin ⋯ "Tornar mensalista" / "Tornar diarista" (disabled with tooltip "Limite atingido 8/8") |
| `AttendanceSummaryCard`, `NextSessionCard`, `UpcomingSessionCard` | "Vagas 7/10" with `Progress`; button by state: `notYet` → disabled "Abre sáb, 31/10 às 20:00", `full` → disabled "Sessão lotada", `priority` → enabled with hint "Você entra no lugar do último diarista confirmado" |
| `AttendanceList` | tag per player; confirmed sorted by `confirmedAt`; players beyond capacity marked "acima do limite"; collapsible "Perderam a vaga" from `bumps[]` |
| `DailyDetailPage`, Home | `Alert` from `myBump` / `nextDaily.bump` ("Você perdeu a vaga para Pedro (mensalista) em sex 21:14") with "Entendi" → `markBumpsSeen` |
| `EditPeladaModal` | "Fuso horário" (`Popover` + `Command` over `Intl.supportedValuesOf("timeZone")`) and "Mensalistas" (number, hint "8 mensalistas hoje · máximo 10 vagas"); **migrated to react-hook-form + zod** (`schemas/pelada.ts`, mirrors `UpdatePeladaRequestDTO`), submit in a hook |
| `CreatePeladaModal` | no new field; sends the browser zone (`Intl.DateTimeFormat().resolvedOptions().timeZone`) |

## Tests

Backend (`Clock.fixed`):
- `DailyAttendanceServiceTest`: diarista before the window → 403, at and after opening → ok; the same 20:00 session opens at different UTC instants for `America/Sao_Paulo` and `America/Toronto` (including Canadian DST); mensalista before the window; full → diarista 409; mensalista into full session bumps the most recent diarista, bumps an admin-confirmed diarista, tie-breaks by `user_id`, joins over capacity without a victim, records the bump and publishes the event; type evaluated at bump time; admin confirm / confirm all bypass window and capacity without bumping; a bumped diarista can confirm again.
- `PeladaServiceTest`: `setMensalista` 403 / 404 / 409 / idempotent; `updatePelada` 409 cases and invalid `timeZone` 400; cleanup on `removePlayer`, `deletePelada`, `deleteDailyData`.
- Controller tests for both new routes (happy path, 400, 403 non-member / non-admin, 404 for a `userId` of another pelada; `bumps/seen` only touches the caller's bumps), and one proving a body sent to `POST /confirm` with `confirmedAt` / a time is ignored.
- Concurrency (Testcontainers Postgres; H2 doesn't reproduce `FOR UPDATE` faithfully): N parallel diarista confirmations for the last spot → exactly one succeeds.
- `FlywayMigrationTest` green with V9; `MigrationDataTest` case for V9 (legacy rows get `confirmed_at = dailies.created_at`).
- E-mail template rendering (escaping, link).

Frontend (Vitest): `getSelfConfirmState` (four states, exactly at `confirmOpensAt`, full with mensalista vs diarista), `formatInZone` in two zones, member type filters, the `EditPeladaModal` zod schema.

## Docs (same PR)

- `core/docs/BACKEND.md`: V9; `PeladaMensalista`, `DailyAttendance`, `DailyAttendanceBump`, `confirmedPlayers` removed; `Pelada` fields; `Clock` + `AttendanceWindow`; permission matrix (set mensalista = admin); new routes and DTO fields; attendance rules (window, capacity, bump, lock); bump e-mail; Known gaps: scheduler and `nextDaily` still use the server zone.
- `client/docs/FRONTEND.md`: `getSelfConfirmState`, `formatInZone`, `useNowUntil`, Badge `membership` variant, `/pelada/:id` and `/daily/:id` route descriptions; remove `EditPeladaModal` from antipatterns #5 and #8.

## Out of scope

- Waiting list (auto-promotion when someone withdraws).
- Tag on teams, ranking and profile (frontend-only later: `isMensalista` is already in the member DTOs).
- Pelada time zone in `DailySchedulerService` and in `nextDaily`'s "from today".
- Upgrade to Java 25 + Spring Boot 3.5 + Flyway 10 and evaluating virtual threads (separate PR; the attendance lock is a database row lock, so it doesn't depend on it).
