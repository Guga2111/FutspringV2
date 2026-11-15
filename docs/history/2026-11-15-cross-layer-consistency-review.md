# Cross-layer consistency review — 2026-11-15

Approved scope: trace frontend/backend flows for sessions and attendance, member
management, and finance. This is a bounded review, not an exhaustive audit.
Existing local documentation reorganization and development files were preserved.

## Confirmed findings and fixes

| ID | Finding and evidence | Impact | Resolution |
| --- | --- | --- | --- |
| C1 | `useDailyDetail` retained data/loading/access state across route changes and accepted every read response; `DailyDetailPage` also retained open dialogs and mutation state. | The URL could point at session B while showing or acting on session A; a late A response could replace B. | Key the complete session subtree by account and route id. Ignore unmounted and superseded reads. Navigation resets data, dialogs and optimistic mutations together. |
| C2 | The detail hook exposed load errors, but the page ignored them and returned `null` without data. Reload failures could leave old actions visible. | A failed initial read produced a blank screen; users had no inline recovery and could continue using a failed reload's stale detail. | Render a semantic destructive Alert and retry button on load failure, hiding stale actions; clear detail on denied reads. |
| C3 | `PeladaService.toSummaries` and `AttendanceService` used `LocalDate.now()` without the injected clock or pelada zone. Client session selection and relative labels used the browser day. | Near midnight, Home and the pelada workspace could disagree about the next session; attendance-ranking boundaries could include/exclude the wrong day. | Derive today from the pelada zone on both sides. Capture one backend instant and group next-session queries by local date (at most three groups worldwide); retain existing same-date/id ordering. Reuse `todayInZone` in the client. |

## Coverage and boundaries

- Traced session detail reads, attendance actions, detail/navigation state,
  next-session summaries and workspace selection, attendance-ranking dates.
- Examined member-action API/schema/state flows and the financial report,
  confirmation and transaction boundary. No additional inconsistency was confirmed
  in those examined paths. This does not certify every member or finance flow.
- Kept service authorization and parent-child checks, financial revision and
  idempotency controls, subscription rules, API DTOs and database schema unchanged.
- The former timezone item was removed from the backend guide's known gaps only
  after fixing both listed backend locations and the related client selection.

## Regression evidence

- `PeladaServiceTest`: one fixed instant with São Paulo/Recife and Kiritimati;
  correct local date per group, correct next-session mapping and two reads for
  three peladas sharing two dates.
- `AttendanceServiceTest`: fixed-clock six-month boundaries west/east of UTC;
  retains the unauthorized-member and unknown-pelada cases.
- `sessions.test.ts`: opposite-side-of-UTC selection, recent activity, relative
  labels and year rollover. Reintroducing browser-day calculation makes the three
  new timezone regressions fail (8 existing cases pass).
- `e2e/daily-navigation.spec.ts`: clearing old data/dialogs on route changes,
  delayed response isolation, leaving an access-denied session, and load-error
  retry and competing same-session reads; runs at 375/1280 px in light/dark.
  Running the first four cases against the original page/hook at `8d327aa`
  produced three failures (old detail/dialog retention, delayed-response overwrite,
  and missing inline error) and one pass. Both source files were then restored and
  all five cases passed in all four projects.

## Final validation

- Backend: `./mvnw test`, JDK 25 — **1,256 tests, zero failures/errors/skips**,
  including PostgreSQL/Redis Testcontainers and migrations.
- Frontend: `bun run lint` — zero errors; the existing TanStack Table warning remains.
- Frontend: `bun run build` — passed.
- Frontend: `bun run test` — **554 tests passed** across 59 files.
- Browser: `bunx playwright test e2e/daily-navigation.spec.ts --workers=2` —
  **20 passed**, all four viewport/theme projects. These are behavioral tests;
  no visual snapshots were changed.
- `git diff --check` — passed for the changed source and documentation.

Integration/browser validation required permission to open local sockets outside
this session's filesystem/network sandbox. The initial sandbox runs failed on
socket access, not application assertions. A missing Playwright browser was
installed only under `/private/tmp/futspring-playwright`. No project dependency
or lockfile was changed.

Trail of Bits `differential-review` was applied to this change and its reachable
read/state flows; no unresolved confirmed security vulnerability was found in
that scope. The full local report and reviewed content hashes are in
`.security-reviews/Futspring_DIFFERENTIAL_REVIEW_2026-11-15.md` (ignored). No PR was
opened or merged; a future PR still requires review against its actual target
and final committed head, including any other branch/local changes it contains.
