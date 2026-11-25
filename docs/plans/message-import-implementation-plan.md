# Message import: faster writes and clearer waiting feedback

Status: implementation plan only. No application changes are included.

## Objective

Reduce the time spent after clicking **“Confirmar e importar”** and make the waiting state clear. Implement the agreed option: optimize persistence and improve feedback without reporting a percentage or server stages that the browser cannot observe.

Documentation and identifiers follow the project's English convention. Product copy remains in pt-BR.

## Current behavior and evidence

- `client/src/components/daily/ImportFromMessageModal.tsx` parses the message and matches players locally. On confirmation, it sends one request and waits for the complete response.
- `client/src/api/dailies.ts` sends `POST /api/v1/dailies/{id}/populate` and receives `DailyDetail`.
- `core/src/main/java/com/futspring/backend/domain/daily/DailyResultsService.java`, `populateFromMessage`, validates the import, removes existing matches/teams/attendance, creates replacements, distributes goals and assists, saves player-match statistics, and changes the status to `IN_COURSE`.
- A statistic row is created for every player of the two teams in each match, including zero-valued statistics. For example, 20 matches with 10 participants each produce 200 statistic rows.
- `PlayerMatchStat` uses `GenerationType.IDENTITY`. As documented in `core/docs/README.md`, Hibernate cannot batch these entity inserts despite `hibernate.jdbc.batch_size=50`; `saveAll` does not solve this.
- `DailyController.populateFromMessage` completes the write transaction and then calls `DailyService.getDailyDetail` to build the response. The browser waits for both operations, with no second detail request after success.

The insert volume is a performance hypothesis supported by the implementation, not a measured production diagnosis. Establish the baseline before claiming a speedup.

## Scope

- Measure the existing import with representative data.
- Reduce database round trips for player-match statistic insertion.
- Keep the existing synchronous endpoint and response contract.
- Add a dedicated, accessible waiting state to the import modal.
- Preserve the user's draft and player associations on failure.
- Update the frontend/backend documentation and relevant tests.

Out of scope: background jobs, new progress endpoints, STOMP progress events, percentages, simulated stages, a global identifier-generation migration, parser redesign, and unrelated frontend state fixes.

## Invariants

1. The backend remains authoritative for admin access, subscription write permission, allowed session status, team identity, membership, and duplicate players.
2. Replacement data is atomic: a failure must restore the previous session, including its teams, attendance, matches, and statistics.
3. Preserve the current imported results and goal/assist distribution rules. Do not remove zero-valued player-match rows: participation counts depend on them.
4. Keep generated IDs, foreign keys, team membership, and attendance consistent with existing entities and migrations.
5. Concurrent writes must not interleave into a partially replaced session. Use the existing session-lock pattern and recheck status under the lock; inspect lock ordering against team/attendance commands before changing it.
6. Return success only after the write transaction commits and the updated detail is available.
7. A local pending state prevents repeated submission. Do not add automatic retries: a transport failure can occur after the server committed.

## Implementation sequence

### 1. Establish a reproducible baseline

- Use a local PostgreSQL 17 instance with the current migrations, never production write requests.
- Prepare representative imports: a small session, a typical session, and a larger session, varying match count while keeping realistic team sizes and scores.
- Include replacement of a session that already has teams, attendance, matches, and statistics.
- Record payload size, resulting statistic-row count, total request duration, write/commit duration, and response-detail assembly duration.
- Run multiple samples after warm-up and compare medians and spread under the same conditions. Record API-to-database placement; local results do not establish production network latency.
- Count database statements as supporting evidence. `support/QueryCounter` observes Hibernate statistics, so verify what it includes for the chosen write path; prepared-statement counts alone do not prove fewer network round trips.
- Keep measurement instrumentation test/local in scope; do not log message contents, tokens, or personal data.

### 2. Batch the dominant insert path

Preferred implementation: a small custom persistence operation in the daily repository area that inserts `player_match_stats` using bounded, parameterized multi-row SQL.

- Preserve JPA mappings and the database's existing ID generation. Omit the ID column so the database supplies it.
- Reuse the current transaction and persistence infrastructure; no new service hierarchy or database dependency is needed solely for this operation.
- Accept the complete planned statistic rows and execute a bounded number of inserts, splitting by a fixed safe parameter/row limit.
- Bind every value. Never interpolate player IDs, scores, or user-supplied text into SQL.
- Validate and construct the complete import plan before destructive writes wherever possible.
- Flush pending JPA removals and parent/join-table writes before native insertion so foreign keys and replacement ordering are correct.
- Check persistence-context consistency when mixing native writes with JPA. Avoid retaining stale statistic entities or clearing the entire context while managed references are still needed.
- Keep existing team, match, and attendance persistence initially. Expand optimization only if the baseline shows those paths materially dominate after statistic batching.
- Preserve all existing rollback and error-handling behavior. The writer must not open an independent transaction or commit chunks separately.

A migration is not expected for this approach. If implementation reveals a required schema change, document it and ship a new Flyway migration; do not edit existing migrations or rely on `ddl-auto`.

### 3. Keep response assembly deliberate

- Preserve the controller's write-then-read flow, with the detail assembled after the write commits.
- Measure that read separately. Reuse existing batched queries and fetch joins if an actual query problem is found.
- Do not add a frontend refetch: the endpoint already returns the detail used by `setDaily(updated)`.
- Do not hide the write behind an early success response or close the modal while the transaction is pending.

### 4. Implement the waiting experience

Reuse the project's shadcn components, semantic tokens, Hugeicons, and existing modal layout.

When submission begins:

- Replace editable review content with a dedicated pending view titled **“Importando sessão”**.
- Show an indeterminate activity indicator, without a percentage or simulated completed steps.
- Show counts derived from the submitted payload: for example, **“3 times · 15 jogadores · 20 partidas”**. Exclude skipped players and label them as the import summary, not completed work.
- Display **“Salvando os dados da sessão. Aguarde a confirmação antes de sair desta tela.”**
- Disable resubmission and all draft-editing controls, including **“Voltar”** and player reassignment.
- Prevent modal dismissal through Cancel, the close control, Escape, and outside click while pending. Use the installed Dialog APIs; any shared primitive extension must preserve current defaults for other consumers.
- Announce the pending state using an appropriate live status region and `aria-busy`, preserve focus behavior, and respect reduced-motion preferences.
- Optionally show **“A importação ainda está em andamento.”** after an elapsed waiting threshold. This is elapsed-time feedback, not evidence of server progress or a timeout. Clean up the timer on completion/unmount.

On success, keep the existing behavior: apply the returned detail, show **“Sessão importada”**, and close the modal.

On failure, restore the review view with the text and associations intact. Show actionable server validation errors. For a network/ambiguous response failure, explain that completion could not be confirmed and ask the user to check the session before retrying; do not claim the server rolled back solely because the browser lost the response.

This is a substantial form change. Follow the repository rule to migrate touched form state/validation to React Hook Form + Zod + shadcn Field, extract request submission into a feature hook, and reuse the existing DTO constraints. Keep parsing and player matching in their existing pure utilities. Do not broaden the migration to other forms.

### 5. Verify correctness and the performance change

Backend integration tests:

- Imported teams, membership, attendance, scores, and participant statistic rows match the existing behavior.
- Zero-goal/assist participation rows remain present; players receive rows only for matches involving their team.
- Representative small and multi-chunk inputs persist correctly; empty statistics do not produce invalid SQL.
- Replacing existing content leaves no duplicate or orphaned rows.
- A failure after destructive changes rolls back the complete import. Verify committed state outside a surrounding test transaction so the test proves the production transaction boundary.
- Invalid/unauthorized/read-only requests preserve existing data and existing response statuses.
- Concurrent imports or import versus another session mutation cannot leave mixed state; use PostgreSQL for lock behavior.
- The response detail can immediately read the committed native-inserted statistics.

Performance evidence:

- Compare the same fixtures and environment before/after, separating writes from response reads.
- Demonstrate fewer statistic insertion executions/round trips, not just a different repository method name.
- Report the observed latency improvement without promising a fixed production duration or using flaky wall-clock thresholds in ordinary CI.

Frontend verification:

- Delay the populate response in Playwright and verify the pending summary, disabled interactions, dismissal protection, and a single request despite repeated clicks.
- Resolve the request and verify the returned session replaces local state and closes the modal without an extra detail GET.
- Reject it and verify the draft/associations survive; cover validation and ambiguous network failures.
- Review mobile at 375 px and desktop in light/dark mode, keyboard behavior, screen-reader status, and reduced motion.
- Follow the existing visual snapshot workflow if the changed modal has snapshots or new coverage is added.

Required commands:

```bash
cd core
./mvnw test
```

```bash
cd client
bun run lint && bun run build && bun run test
```

Run PostgreSQL/Testcontainers checks with Docker available; explicitly report any skipped tests. Run the focused Playwright scenarios and review intentional visual diffs.

### 6. Update documentation and deliver

- Update `core/docs/README.md` with the import write strategy, transaction boundary, and relevant performance/test guidance. Clarify that entity-based IDENTITY inserts remain unbatched while this native bulk path is an explicit exception.
- Update `client/docs/README.md` with the import hook, schema, waiting/error behavior, and tests. Remove `ImportFromMessageModal` from the applicable form/direct-API antipattern entries only once those deviations are actually resolved.
- Keep endpoint, DTO, frontend types, and schema aligned. No API shape change is planned.
- Include before/after measurements, correctness evidence, and any remaining bottleneck in the implementation report.

## Acceptance criteria

- [ ] Representative imports perform fewer statistic insertion round trips and show measured latency results.
- [ ] Import semantics, permissions, subscription checks, atomicity, and concurrency protection hold.
- [ ] The modal immediately shows accessible activity feedback and an accurate payload summary.
- [ ] No fabricated percentage, server stage, or early success is shown.
- [ ] Pending imports cannot be edited, resubmitted, or dismissed through modal controls.
- [ ] Failures preserve the draft and distinguish known rejection from uncertain transport outcome.
- [ ] Success applies the returned detail without a redundant request.
- [ ] Documentation and required checks are complete, with skipped/environment-limited checks disclosed.

## Rollback and limits

Keep the optimization isolated to the import statistic writer so the application change can be reverted without a schema rollback. Do not execute imports or deploy as part of preparing this plan.

Feedback cannot expose actual server progress under the unchanged request/response contract. If measured duration remains unacceptable after write optimization, use those measurements to decide whether a separate follow-up should add real progress reporting or further optimize response assembly.
