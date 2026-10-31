# PR4 — Convert DTOs to Java records

## Approved scope

Convert domain request and response DTOs, including nested DTOs, to records. Preserve application behavior and the frontend contract, except for the approved removal of unused duplicate boolean keys.

- Retain existing builders and static mapping factories. Replace DTO getters and setters in production and tests with record accessors and complete construction; no compatibility getters or generic copy framework.
- Supply statistics award counts before constructing responses.
- Preserve validation annotations, nested validation, Portuguese messages, JSON names, and null-inclusion rules.
- Preserve omitted creation defaults (especially 2 teams and 5 players per team), nullable partial-update fields, and rejection of explicitly invalid values.
- Remove duplicate `admin`, `finished`, `confirmed`, and `mensalista` keys only where the corresponding `is…` fields remain. The frontend already uses the retained fields.
- Leave entities, migrations, business rules, permissions, dependencies, and unrelated Java cleanup untouched.

## Contract ownership and rollout

REST controllers and STOMP notifications write the domain DTOs, using service/mapper factories; the SPA reads their JSON through `client/src/api/*` and `client/src/types/*`. Request DTOs are read by Jackson and validated at the controller boundary before services use them. Builders and constructors are internal Java callers, migrated in the same change.

The wire shape stays compatible with both API versions for the existing client: retained keys and request payloads keep their meaning; only unused duplicate boolean response aliases disappear. No stored data or database shape changes, and no migration is required. Roll forward by deploying the verified API; rollback by reverting the PR or deploying the prior API image. Both API versions remain compatible with the unchanged SPA. There is no backfill or destructive database step.

## Verification

- Run contract snapshots and request round-trip tests; allow only the approved duplicate-key removals.
- Add focused regression coverage for omitted creation defaults, explicit invalid settings, partial updates, nested validation, and nullable response fields.
- Run `./mvnw verify` with Docker available so PostgreSQL migration tests execute.
- Run frontend lint, build, and tests if frontend code becomes necessary; otherwise keep frontend changes documentation-only.

## Documentation and delivery

- Commit this approved spec before implementation.
- Update backend record conventions and frontend contract documentation; reconcile the original design acceptance criteria with the boolean-key exception.
- Keep approved snapshot removals in a separate commit, listing the removed keys.
- Commit only task-related changes on `dev`; leave existing unrelated untracked files untouched.
- Open a PR from `dev` to `main` with validation results, removed keys, and confirmation that no migration is required. Do not merge.

## Implementation evidence — 2026-10-30

- All 49 top-level domain DTOs and their 22 nested DTOs are records (2 top-level DTOs were already records). Existing builders and mapping factories remain; stats factories receive award counts before construction.
- Jackson 3 rejects omitted primitive constructor components. DTO-local Jackson builders preserve the previous creation defaults and zero-valued match/player-stat inputs while keeping explicit nulls invalid; no global mapper setting changes.
- The 51 original contract tests and 25 focused regression cases pass without snapshot regeneration. Three controller regressions verify omitted creation defaults and explicit zero/null rejection through HTTP.
- `JAVA_HOME=…/25-open DOCKER_HOST=unix://…/docker.sock ./mvnw -B verify`: **766 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. `FlywayMigrationTest` (2 tests), `MigrationDataTest` (5), and `AttendanceConcurrencyTest` all executed against PostgreSQL 17.
- Snapshot comparison against the pre-conversion baseline permits exactly 11 duplicate-key deletions across six files. Every removed key retains its corresponding `is…` value; no other snapshot or request-fixture changes. Original property ordering is retained to keep the diff limited to deletions.
- All existing validation and JSON annotations are preserved. No entity, migration, dependency, permission rule, or frontend source changes. Frontend checks were not run because its changes are documentation-only, as approved above.
