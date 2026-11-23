# Database schema and migrations

> How Flyway owns the schema, the migration history and the migration rules. Part of the [backend guide](README.md).

## Database schema & migrations

### How the schema is managed

- **Flyway 12** (`spring-boot-starter-flyway` + `flyway-database-postgresql`, versions managed by Spring Boot; since Flyway 10 each database lives in its own module) runs `src/main/resources/db/migration/V*.sql` at startup, before JPA.
- Hibernate runs with `ddl-auto=${DDL_AUTO:validate}`: it never changes the schema, only checks that the tables match the entities, and the app fails to start if they don't. `DDL_AUTO` is not set by `deploy.sh` or the compose files (the deploy fails if `.env` sets it); keep the default.
- `spring.flyway.baseline-on-migrate=true` + `baseline-version=1`: a database that already has tables but no `flyway_schema_history` (production, an old dev volume) is marked as V1 without running it, and only later versions run. An empty database runs V1 and builds the whole schema.
- `V1__baseline.sql` is the schema Hibernate generated from the entities on 2026-10-03 (PostgreSQL 15, `pg_dump --schema-only`), not a dump of production. Production's tables were built by `ddl-auto=update` from the same entities. Checked against a copy of production on 2026-10-04: same columns and constraints, except two leftover nullable columns `daily_awards.puskas_winner_id` / `wiltball_winner_id` (all null, FKs to `users`) that no entity maps; V4 drops them.
- Production (Supabase), dev (`docker-compose.dev.yml`) and the migration tests all run PostgreSQL 17, which Flyway 12 supports (no "newer than this version" warning).
- Tests: H2 `create-drop` with Flyway disabled for the unit/integration tests; `FlywayMigrationTest` (Testcontainers, `postgres:17-alpine` like production, skipped without Docker) runs every migration on an empty Postgres and validates it against the entities; `MigrationDataTest` runs the data-changing migrations (V4, V5, V8, V9, V19, V22) and V11 / V21 compatibility against production-shaped rows.

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
| V12 | Adds isolated finance tables, exact monetary components, idempotency/audit, partial uniqueness for active charges and pending notices, unique notice receipts/reversals, private image metadata and preservation guards; no historical debt/payment inference. See Pelada finance |
| V11 | Creates `pelada_invitations`, `pelada_invite_links`, `pelada_link_blocks` with FKs/indexes and partial uniqueness for pending invitations and unrevoked links; adds nullable `notifications.invitation_id` and invitation/join types. Additive; existing membership and sporting history remain unchanged |
| V13 | Creates `user_tours` (PK `(user_id, tour)`, CHECK on `tour`, FK `ON DELETE CASCADE`): guided tours each user has seen; non-destructive |
| V14 | Indexes `finance_audit(pelada_id, created_at DESC)` and `finance_movements(pelada_id, effective_date DESC)` for the date filters of `/finance/audit` and `/finance/movements`; non-destructive |
| V15 | Designates every current admin and creator (members only) as responsável financeiro in `finance_managers` (`ON CONFLICT DO NOTHING`), since only responsáveis manage the finance from now on; non-destructive |
| V16 | Adds nullable `users.terms_version` (`VARCHAR(20)`) and `terms_accepted_at` (`TIMESTAMPTZ`): the accepted terms/privacy version, recorded at registration; no backfill (older accounts stay null); non-destructive |
| V17 | Adds `users.deleted_at` (`TIMESTAMPTZ NULL`): when the account was deleted (anonymized). Additive, non-destructive. |
| V18 | Creates `push_subscriptions` (FK `users` `ON DELETE CASCADE`, unique `endpoint`, index on `user_id`) and adds nullable `notification_preferences.push_enabled` (null = the category's default, so the existing rows keep meaning e-mail only + default push); additive, no backfill, non-destructive |
| V19 | Adds `users.platform_admin` (`NOT NULL DEFAULT false`) and `users.trial_used_at`; creates `pelada_subscriptions` (unique `pelada_id`, `ON DELETE CASCADE`, CHECKs on `kind` / `plan` / `claimed_plan` and a valid until for `TRIAL`/`PAID`, index `(kind, valid_until)`), `subscription_payments` (`pelada_id` `ON DELETE SET NULL` + name snapshots, indexes on `pelada_id`, `paid_on` and the user FKs) and `subscription_events` (`ON DELETE CASCADE`, CHECK on `type`, index `(pelada_id, created_at DESC)`). Backfill: every existing pelada gets a `TRIAL` until the migration + 30 days with a `CREATED` event (no actor), and their creators get `trial_used_at = now()`; additive, non-destructive |
| V20 | Adds nullable `users.created_at` and `users.updated_at` (`TIMESTAMPTZ`); `created_at` is backfilled from `terms_accepted_at` (the registration instant since V16), so older accounts stay null; additive, non-destructive |
| V21 | Adds nullable `notifications.valid_until` (`TIMESTAMPTZ`), the types `SUBSCRIPTION_ENDING`, `SUBSCRIPTION_PAST_DUE`, `SUBSCRIPTION_PIX_CLAIMED` to `notifications_type_check` and `BILLING` to `notification_preferences_category_check`, and the unique partial `uq_notifications_subscription (pelada_id, type, valid_until)` for the two scheduled notices; additive, non-destructive |
| V22 | Normalizes `users.email` to `LOWER(TRIM(email))` and adds the unique index `uq_users_email_lower ON users (LOWER(email))` (also used by the user search's exact e-mail match). Production had no e-mail to change and no duplicate by case (checked 2026-11-11, 56 accounts); a duplicate would fail the migration and the deploy; non-destructive |
| V23 | Adds email-verification timestamp and hashed single-use verification state; leaves existing accounts unverified and increments token_version to revoke sessions |
| V24 | Adds users.email_link_version (default 0) for individual user preference-link revocation |
| V25 | Adds `users.row_version BIGINT NOT NULL DEFAULT 0` for JPA optimistic locking; existing credentials and revocation counters unchanged; additive |

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

## Antipatterns

Don't introduce these. Numbers are shared by the whole backend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 19 | Schema changes made only by editing entities and relying on `ddl-auto=update` in production | Flyway migration in the same PR; `validate` in prod | — |
| 20 | New `NOT NULL` column without a default/backfill, or a rename in one step | Expand/contract (see [Migration rules](migrations.md#migration-rules)) | — |
| 21 | Editing a migration that already ran | New migration | — |
