# Database backup and recovery

**Operational acceptance pending; task #43 remains open.** The owner subsequently authorized R2 setup,
its metered-billing exposure and continuation through verified backup/isolated recovery/alert acceptance.
This supersedes the initial strict zero-cost requirement for R2 only; no paid upgrades, recovery targets
or production restore/cutover are authorized. Deploy blocking and seven-day deletion protection remain.
See the [approved specification](../../docs/plans/2026-11-19-r2-database-backup-spec.md).

## Ownership and invariants

`backup.py daily|pre-deploy` is the only backup implementation. Python 3.10+ standard library,
Docker with a pre-staged `postgres:17-alpine`, and AWS CLI v2 are required on the Linux host.
Credentials are host-only; the API `.env` and container receive none of them. No application changes.

The source must be PostgreSQL 17, direct/session endpoint on 5432, verified TLS and complete SELECT
on every public table/sequence. The script exports a read-only repeatable-read snapshot, then uses
it for preflight, data fingerprints and `pg_dump -Fc --compress=gzip:6 --schema=public --no-owner --no-acl`.
All application public objects and Flyway history are included. Roles, ownership and grants must
be provisioned explicitly on a recovery target. Public extensions, partitioned/foreign tables and
dependencies on other application schemas fail preflight: review scope before supporting them.
RLS flags are preserved and compared during the drill; the backup role must read without filtering
(`row_security_active=false`, normally through `BYPASSRLS`). Public RLS policies currently fail preflight
until their recovery roles/dependencies are reviewed. Never add `--enable-row-security` to pg_dump or
disable production RLS to make a backup pass. Source metadata confirmed PostgreSQL 17.6 and RLS enabled
on `users` without policies; complete dedicated-role access and full preflight remain acceptance checks.
Provider services and cluster-wide roles are outside this archive.

The host flock serializes both callers, with a 30-second wait and 30-minute overall command deadline.
Database connect/lock timeouts are 10 seconds; statement timeout is 20 minutes. Source clients
live in a dedicated restricted container for at most 30 minutes; staging is private and removed
in `finally`. An uncatchable host/process kill may leave `staging-*`: remove only after acquiring
`run.lock` and checking for surviving `futspring-backup-*` containers. Never delete the lock file.

Uploads use single-part S3 PUTs (archive ≤512 MB), Standard storage, unique date/UUID prefixes and
at most two full attempts. No LIST, multipart, delete, overwrite of previous keys or background retries.
AWS implicit retries are disabled. Each attempt downloads and verifies the exact archive size/SHA-256,
then uploads and reads back the manifest byte-for-byte. Only then can verified snapshot metrics and
state advance. The manifest includes source time/version, client version, optional commit, size/hash,
source wire-byte measurement, all table counts/fingerprints, constraints/indexes and Flyway digest.
Fingerprints are validation aids, not cryptographic proof of data authenticity. SHA-256 verifies transfer,
not the trustworthiness of a dump or its author; keep recovery credentials and evidence independently.

## Financial and activation gates

**8 GB is a prospective local ceiling, not a zero-invoice guarantee.** R2 has no applicable US$0
billing hard cap. Workers spending controls and budget alerts do not stop R2 usage. Do not turn
`zero_additional_cost_enforced` on merely because the free allowance is available. Leave it false for R2.
The financial gate alternatively accepts `owner_r2_usage_charges_accepted=true`, with a private
`billing_control_evidence` reference to the owner's explicit R2 billing-risk acceptance. This does not
approve another provider, paid Supabase upgrades or recovery resources, raise any local limit, establish
known account/source budgets, or satisfy backup/restore/alert acceptance. Without enforceable zero-cost
controls or that explicit R2 acceptance, activation stays blocked. Never infer acceptance from free headroom.
References: [R2 pricing](https://developers.cloudflare.com/r2/pricing/),
[Cloudflare spending controls](https://developers.cloudflare.com/billing/manage/spend-management/),
[Supabase egress](https://supabase.com/docs/guides/platform/manage-your-usage/egress).

`activation.example.json` is deliberately disabled. It also records private Standard bucket, lock/retention,
expiration of review, and backup/restore/Telegram evidence. After financial feasibility is resolved and
validation is separately authorized, `--validation-only` permits acceptance rehearsals with
`owner_validation_accepted`; it cannot satisfy the deploy command's operational activation check.
Only after real backup, recovery, notification and owner acceptance should ordinary runs be permitted.

`budget.example.json` is deliberately unknown. It is a private, durable **account-wide reservation ledger**,
not usage inferred from one bucket. An operator must reconcile it using authoritative complete account
usage, incomplete uploads, retained objects, other buckets, pending reservations, pagination, monitoring,
drills and other writers. No provider inventory is queried automatically (those requests also cost budget).
The evidence must be ≤24 hours old, in the current UTC month and valid for the next full 30-minute run.
Every uncoordinated writer must be stopped or have a proven worst-case envelope reserved under
`external_reserved`; otherwise `writers_coordinated` must remain false. A bucket token proves neither
account-wide usage nor coordination. Keep budget/approval/config ownership private to the host operator.

Before **any** source/S3 request, persist these worst-case reservations atomically with fsync:

| Resource | Per-run reservation |
|---|---|
| Storage | `2 × (max_archive_bytes + 1 MB manifest)`; counts uncertain successful PUTs |
| R2 Class A | 4 requests (two archive + two manifest PUTs) |
| R2 Class B | 4 requests (two archive + two manifest verification GETs) |
| Supabase | Measured conservative `source_run_reservation_bytes`, including preflight/fingerprints, TLS/protocol and in-flight margin |

Limits never exceed 8 decimal GB, 800k Class A, 8M Class B, or 5 decimal GB source egress/month;
use lower account/organization allowances when appropriate. Each resource includes `used`,
`external_reserved`, `local_reserved` and `safety_margin`; all four count. Source safety must reserve
normal application traffic for the **whole remaining billing period**. Database dashboard size and local
dump compression do not measure source egress. Confirm the actual plan and shared organization usage.
Measure a permitted synthetic rehearsal and a separately authorized real rehearsal before setting envelopes;
calculate projected 30 dailies, expected deploys, drills and growth. If that cannot fit, do not activate.

A source-container watchdog samples RX+TX every 100 ms and stops clients before the reserved envelope,
leaving the separately measured `source_inflight_margin_bytes`. Exceeding the stop level or final envelope
invalidates the ledger and blocks future copies until reviewed. Polling and delayed provider accounting
cannot enforce a byte-exact billing cap; this is another reason the financial gate remains mandatory.
No source retry; S3 retries are already fully reserved. All reservations remain charged locally after
failures, timeouts or ambiguous outcomes. Never subtract objects merely eligible for expiration, and never
automatically reset a ledger on month rollover. To refresh/reconcile, hold the same `run.lock` with `flock`,
account for all actual and still-outstanding usage, retain an audit copy, then atomically replace the ledger.
Never reset counters just to unblock a deploy. Unknown/missing/exhausted state blocks backup and deploy.

## Installation and external setup

1. Record the financial decision first. Account/location, Supabase plan/remaining egress, operator and
   independent recovery-credential custody need verified evidence. Use existing VPS resources; no paid target.
2. With a separate bucket-admin credential **outside the VPS**, create/review a private Standard bucket,
   public access/r2.dev/custom public domains disabled. Apply lifecycle expiration for `database/daily/`
   after 30 days and `database/pre-deploy/` after 7 days. Apply R2 **bucket lock** to `database/` for
   seven days (not S3 Object Lock, which R2 does not implement). Rules cover archive and manifest.
   Reviewable request bodies: `r2-lifecycle.example.json` (S3 lifecycle) and `r2-lock.example.json`
   (Cloudflare bucket-lock API). Apply only after authorization, with admin credentials off the VPS.
   Verify with synthetic PUT/delete/overwrite/expiry tests and record exact settings. Lock-rule admins
   can change protection. Resource creation and real transfers require explicit operational authorization.
3. Pre-stage reviewed AWS CLI v2, Python and the PostgreSQL image; record image digest and tool versions.
   `sudo ops/backup/install.sh` idempotently copies tooling/units and reloads systemd; it never starts
   or enables anything and never overwrites credentials/budgets. Run outside a backup/deploy window.
4. Copy the JSON examples into the indicated paths, `chmod 600`, owned by the execution user (production:
   root). In `/etc/futspring-backup/postgres`, install `pg_service.conf`, a trusted `root.crt` and a
   `pgpass` line `host:5432:database:backup_reader:password` (libpq escaping applies). Do not use URLs,
   shell environment passwords, transaction pooler 6543 or `sslmode=require`. Grant SELECT on all existing
   tables/sequences and default privileges for future Flyway objects; test deny/partial-read failures.
   Install bucket-scoped object read/write S3 credentials in `r2.credentials`; keep admin and recovery
   credentials elsewhere. Never print these files or run with shell tracing/AWS debug.

   For the current `postgres`-owned schema, a database administrator can provision the reader with:

   ```sql
   CREATE ROLE futspring_backup_reader LOGIN BYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 6;
   GRANT CONNECT ON DATABASE postgres TO futspring_backup_reader;
   GRANT USAGE ON SCHEMA public TO futspring_backup_reader;
   GRANT SELECT ON ALL TABLES IN SCHEMA public TO futspring_backup_reader;
   GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO futspring_backup_reader;
   ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT ON TABLES TO futspring_backup_reader;
   ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT ON SEQUENCES TO futspring_backup_reader;
   ALTER ROLE futspring_backup_reader SET default_transaction_read_only=on;
   ```

   Set the generated password with psql `\password futspring_backup_reader`, outside command history/logs.
   Store it only in the private passfile; shared-pooler usernames include `.PROJECT_REF`. Verify the role
   has complete SELECT and no table write/schema-create privileges, including privileges inherited from
   PUBLIC. `BYPASSRLS` alone grants neither SELECT nor writes. Keep the API's credentials unchanged.
5. Populate measured budget and financial evidence. Authorize acceptance validation, run a real
   `backup.py daily --validation-only`, download its archive/manifest using an independently held
   recovery credential under a **separately reserved GET/drill budget**, and run the drill below.
   Downloaded files contain personal data: private staging, no CI artifacts or Git, delete after acceptance.
6. Redeploy the observability configuration in an authorized window. Run both ordinary backup kinds only
   after acceptance. Test Grafana contact point **telegram → Test**, plus synthetic failure, stale/missing
   metrics, budget and restore-age alerts. Record actual Telegram receipt, sizes, duration and recovery time.
7. Only after owner acceptance: `systemctl enable --now futspring-backup.timer`. Check
   `systemd-analyze calendar '*-*-* 03:00:00 America/Recife'`, `systemctl list-timers`, a daily execution,
   and a pre-deploy execution. `Persistent=true` catches a missed daily at host restart. Review monthly.

## Operation and local tests

```sh
python3 -m unittest discover -s ops/backup/tests -v
BACKUP_DOCKER_TESTS=1 python3 -m unittest discover -s ops/backup/tests -v
# PromQL tests additionally need the test-only requirements and a pre-staged Prometheus image:
python3 -m pip install -r ops/backup/tests/requirements.txt
python3 ops/backup/tests/check-alerts.py
# Only on a configured, accepted host:
sudo python3 /opt/futspring-backup/backup.py daily
sudo python3 /opt/futspring-backup/backup.py pre-deploy --commit <40-character-sha>
sudo journalctl -u futspring-backup.service --since today
# Local files previously downloaded and verified under a reserved recovery budget:
python3 ops/backup/restore-drill.py /private/staging/database.dump /private/staging/manifest.json
# On the accepted host add: --metrics-dir /var/lib/node_exporter/textfile_collector
```

Tests use synthetic TLS, all repository migrations, synthetic accounts, stats and finance, and in-memory S3
fault injection; never contact R2/Supabase. The drill accepts **no DSN**: a new PostgreSQL container, no network,
no published ports, no host/credential mounts, bounded CPU/memory/tmpfs, single-transaction error-stop restore.
It compares every table's count/fingerprint, constraints, indexes, RLS flags and Flyway history against the source snapshot,
and tests sequence nextval against column maxima plus FK/NOT NULL/CHECK/uniqueness rejection. Constraint/index
inventories are structural: PostgreSQL may reparse equivalent casts differently when restoring the archived DDL.
It discards the target even on failure. Monthly drills must
use an actual downloaded recovery point; synthetic CI success is not operational acceptance. Reserve remote
download requests and local capacity before retrieving a copy. The script itself performs no remote downloads.

Node-exporter reads atomic `.prom` files in `/var/lib/node_exporter/textfile_collector` (public numeric metrics,
no object IDs or credentials). Fixed `kind=daily|pre-deploy`: last attempt, result (-1 running, 0 failure,
1 verified), budget-blocked, snapshot timestamp, size, duration, prospective storage. Grafana alerts on failure,
budget, missing/broken metrics, daily snapshot >26 h, storage >7.2 GB and restore test >31 days/missing.
Predicted retention expires independently even when jobs fail: investigate freshness alarms immediately.
Monitoring is on the same host and cannot independently alert on complete host loss.

## Deploy transition

`scripts/deploy.sh` requires the installed shared `pre-deploy` command before either frontend rsync,
new `.env` publication or API/Flyway startup. Missing config/tooling, budget, access, upload or hash
failure exits without publishing. Until activation evidence exists **deploys using this version are blocked**;
the running API is not stopped. Stage operational prerequisites before merging to the auto-deployed `main`.
There is no fallback/bypass to the optional legacy dump. Preserve `~/projects/futspring/backups/*.sql.gz`
and their original permissions: the implementation does not remove or rotate them. They are legacy plain SQL:
`gzip -dc legacy.sql.gz | psql -X -v ON_ERROR_STOP=1` into a separately prepared isolated target, not `pg_restore`.
They have no new verification manifest; test/review separately. Disable with `systemctl disable --now
futspring-backup.timer`; that deletes no backups and restores nothing. Disabling does not unblock deployments.

## Manual incident recovery and planned migration

1. Stop **all** writers (API, schedulers, integrations, other deployments), declare maintenance, preserve the
   original database and independently held evidence. Choose a known healthy snapshot and matching app commit;
   reserve transfer/recovery capacity, download both files with recovery credentials, verify SHA-256 and size.
2. Rehearse with the drill. For real recovery, provision a **separate** compatible target/roles/grants manually;
   use libpq service/passfile with verify-full, `pg_restore --exit-on-error --single-transaction --no-owner --no-acl`.
   Never run `--clean` against production. Compare data, finance balances/receipts, stats, constraints, sequences,
   Flyway history and application behavior in isolation. Initially disable Flyway (`SPRING_FLYWAY_ENABLED=false`),
   dev seed, email, push and all outbound access; do not let normal schedulers mutate the rehearsal.
3. Reconcile post-snapshot deletions, permission revocations and financial changes from available independent
   evidence before reopening. Record unrecoverable loss. Invalidate access-token versions and refresh sessions
   as appropriate; clearing Redis alone does not invalidate all access tokens. A 03:00 copy after a 16:00
   incident may lose 13 hours. Measure recovery duration; do not promise an unmeasured RTO.
4. Validate target ownership/grants and TLS; update the API connection **and its deployment secrets** and the
   future backup source. Restart, check readiness and representative user/finance flows, then reopen. Keep the
   old database write-protected until recovery acceptance. No automatic production rollback.
5. For a **planned migration**, rehearse first, then stop every writer and make/verify a **fresh final backup**
   after the freeze using the same tool with reserved budget. Restore that copy, validate and switch connections
   as above. Yesterday's daily loses intervening writes. Once the new target accepts writes, switching back
   requires reconciliation; it is not a lossless rollback. Production cutover needs separate authorization.

This restores database metadata/data only: photos, uploaded files, finance QR files, Redis and Supabase services
are separate. Never include temporary finance receipts in durable file backups. Missing photos/files and
provider-specific authentication/services must be handled by their own recovery procedures.
