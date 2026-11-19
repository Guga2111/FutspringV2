# Database backup and recovery to Cloudflare R2 — spec

Date: 2026-11-19. Status: **Owner decisions confirmed; zero-cost feasibility must be resolved before production activation.**
Oriens: Futspring task **#43 — Agendar backup do banco e testar restauração**.

## 1. Goal and scope

Keep verified offsite recovery points for the Futspring application database. Support both incident recovery and a planned move to another compatible PostgreSQL database.

Include the application schema/data, sequences, indexes, constraints and `flyway_schema_history`. Before implementation, confirm PostgreSQL version, schema selection/dependencies and complete read permissions. Expect PostgreSQL 17 and schema `public`, based on [backend documentation](../../core/docs/BACKEND.md).

Photos, uploaded files, finance QR files, Redis and Supabase-managed services are outside this database backup. Temporary finance receipts must remain excluded from durable file backups. Image storage remains task #44.

## 2. Recommended settings

| Item | Proposed setting |
|---|---|
| Destination | Private R2 Standard bucket `futspring-backups-prod`; public access disabled |
| Daily schedule | 03:00 America/Recife, independent of API uptime/deployments |
| Daily retention | 30 days |
| Pre-deploy retention | 7 days, under a separate prefix |
| Format | PostgreSQL custom archive (`pg_dump -Fc`) with native gzip compression |
| Integrity | SHA-256 verification after downloading the uploaded object again |
| Credentials | Backup-specific host configuration, outside Git and the API container |
| Encryption | Verified TLS in transit and R2-managed encryption at rest |
| Restore tests | Before activation, then monthly and after relevant schema/tool changes |
| Monitoring | Existing Prometheus/Grafana stack and Telegram contact point |
| Deployment failure | Block publication/migrations if the required offsite backup cannot be verified |
| Deletion protection | R2 bucket lock for 7 days; ordinary deletion/overwrite is blocked during that period |
| Additional provider cost | **US$0. No paid overages, upgrades or paid recovery resources are authorized** |

The owner explicitly confirmed deployment blocking, seven-day deletion protection and no additional cost. Other settings remain the recommended defaults. This confirmation does not start an infrastructure rollout or authorize billable exposure. Client-side encryption is not part of the initial proposal; introducing it requires an explicit key-recovery design.

## 3. How backup execution works

A systemd timer on the VPS calls one shared script under `ops/backup/`. It uses the PostgreSQL client in Docker and AWS CLI v2 to access R2's S3-compatible endpoint. The deployment script calls the same implementation. No Spring job, new API endpoint or frontend feature is needed.

1. Acquire a shared host lock so daily and pre-deploy executions cannot overlap; bound lock waits and total runtime. Validate the source's available egress budget before dumping. Unknown or insufficient headroom fails the run and blocks a dependent deployment.
2. Create a private temporary directory and dump a consistent application snapshot through the Supabase direct/session endpoint, with verified TLS. Fail on permission errors or incomplete schema selection.
3. Check dump exit status, nonempty output and required archive entries. Custom format is already compressed; do not gzip it again.
4. Before uploading, validate destination storage/request headroom as described below. Unknown or insufficient headroom fails the run and blocks a dependent deployment. Calculate SHA-256 and upload under a unique UTC timestamp/run id, for example `database/daily/2026-11-19/<run-id>/database.dump`.
5. Download again, compare exact size/hash, then publish/read back a manifest with snapshot time, versions, application commit when known, size and hash. Only verified copies advance the last-success timestamp.
6. Remove local temporary files and release the lock on success or failure. Retry transfers only within a bounded deadline. Previous backups remain untouched.

A valid hash proves transport integrity; it does not replace a restore test. Do not treat an ETag as a SHA-256 checksum. Never expose database URLs/passwords in arguments or logs: use libpq settings and a protected passfile; scope R2 credentials to the backup bucket and keep bucket-admin credentials off the VPS.

## 4. Retention, space and alerts

R2 lifecycle rules expire daily objects/manifests after 30 days and pre-deploy objects/manifests after 7 days. Every run adds a recovery point; it never replaces yesterday's copy. Expiration is asynchronous and runs independently of backup success. If jobs remain broken beyond the retention window, old copies can all expire; stale-backup alerts are required. Do not automatically shorten retention to control costs.

The reported **228 MB** Supabase database size is not the measured compressed archive size. The earlier estimate, `228 MB × 30 = 6.84 GB`, ignores compression, growth and extra deploy copies. One 228 MB deploy copy per day retained for seven days would bring the illustrative total to about **8.44 GB**, before expiration delays. Measure an actual dump and account-wide R2 usage before activation.

As checked on 2026-11-19, R2 Standard includes 10 GB-month; excess storage is US$0.015/GB-month, with separate operation allowances. The allowance is not a billing hard cap. See [R2 pricing](https://developers.cloudflare.com/r2/pricing/).

### Mandatory zero-cost policy

- Do not enable paid plans, accept overages, create paid recovery targets or treat an alert as permission to spend. Review the complete cost path, including Supabase egress, R2 operations and existing VPS resources.
- Add a conservative **8 GB prospective storage ceiling** for controlled R2 uploads. Count the next complete archive/manifest, retained objects, incomplete uploads and outstanding reservations; do not subtract objects merely eligible for lifecycle deletion. Shared account usage and concurrent writers must be accounted for. A bucket-scoped token alone cannot prove account-wide headroom.
- Bound monthly request usage, including inventory pagination, multipart transfers, retries, integrity downloads, monitoring and drills, below the available free operation allowances. Unknown account usage, missing budget state or exhausted allowances must fail closed; a storage-only check is insufficient.
- When a budget check fails, skip new offsite copies, notify through the existing stack and block the associated deployment. Do not delete protected/history copies early, lower retention, disable the lock or bypass verification to free space. The live API is not stopped, but backup freshness degrades; after the existing retention window, copies can expire. This tradeoff must be visible in alerts.
- These local controls reduce accidental usage; **they are not a provider-enforced guarantee of a zero invoice**. Cloudflare's documented spending limits cover Workers/Durable Objects, not R2, and budget alerts do not stop usage. Do not claim that R2 has a configurable US$0 hard cap. [Cloudflare spending controls](https://developers.cloudflare.com/billing/manage/spend-management/)
- **Activation gate:** verify the account's effective billing controls and whether this setup can meet the owner's strict no-cost requirement. If billable exposure cannot be ruled out, do not activate R2 or silently accept that risk. Record the limitation and evaluate a storage option with enforceable no-overage behavior; provider selection then needs owner review. Development of local tooling can proceed independently of this gate.
- Confirm the actual Supabase plan and remaining organization-wide egress. The Free plan currently includes **5 GB uncached egress**, shared with application traffic; manual backups through the pooler consume it. The 228 MB dashboard size is not the wire-byte measurement, and compression on the VPS does not itself reduce bytes already transferred from Supabase. Measure actual source transfer and reserve normal app traffic before fixing the backup frequency. Free-plan overuse can restrict service; paid plans require their applicable billing controls. [Supabase egress](https://supabase.com/docs/guides/platform/manage-your-usage/egress), [cost controls](https://supabase.com/docs/guides/platform/cost-control)

Use node-exporter's textfile collector, with atomic metric-file updates and fixed daily/pre-deploy labels, to publish last attempt/result, verified snapshot time, duration and size. Add Grafana notifications for failures/budget-blocked runs, a daily snapshot older than **26 hours**, missing/broken monitoring, storage nearing the **8 GB prospective ceiling**, and overdue monthly restore tests. Test actual Telegram delivery. Monitoring shares the VPS and therefore does not provide independent host-loss alerting.

## 5. Incident recovery

Recovery is manual. The daily timer must never automatically roll production back.

1. Stop application and scheduled-job writes. Preserve the original database if available.
2. Choose a known healthy recovery point, download archive/manifest with a recovery credential kept outside the VPS, and verify SHA-256.
3. Restore with `pg_restore` into a separate compatible PostgreSQL target, with error-stop behavior and correct ownership/permissions. The drill tool creates its own disposable target and cannot target production by accepting an arbitrary DSN.
4. Verify tables/data, financial/statistical invariants, sequences, constraints and Flyway history. Test the matching application version in isolation, with migrations initially disabled, no dev seed, and outbound email/push disabled and network access restricted.
5. Reconcile relevant post-snapshot account deletions, access revocations and other recoverable changes before reopening. A historic dump alone cannot reconstruct later actions; document the available evidence and any remaining data loss. Invalidate existing access/refresh sessions as needed; clearing Redis alone does not revoke every issued access token.
6. Update the production connection and its deployment secret source, restart, validate and reopen. Keep the old database until recovery is accepted. Account for unavailable image files and provider-specific configuration outside the dump.

Only changes present at snapshot time are recovered. For example, restoring a 03:00 snapshot after a 16:00 incident can lose 13 hours of changes. Actual loss depends on the latest healthy copy and whether newer data can be reconciled. A recovery duration must be measured rather than promised.

## 6. Planned production database migration

1. Prepare another PostgreSQL target and prove a rehearsal restore/application check there.
2. During a maintenance window, stop every production writer, including jobs and any overlapping deployment.
3. Generate and verify a **fresh final backup** after writes stop, then restore it to the destination. Using only the previous daily backup would leave newer changes behind.
4. Validate the destination, permissions, schema compatibility and application behavior. Update the API connection, future deployment secrets and backup source configuration.
5. Reopen against the new database; keep the old one protected from writes. Once the destination receives new writes, switching back is not a lossless rollback without reconciling them.

The same archive tooling supports this process, but does not automatically recreate an entire Supabase project or transfer files/Redis. Production cutover is a separately authorized operation.

## 7. Implementation and acceptance

- Add the shared backup script, safe isolated restore drill, systemd units, non-secret examples and a concise runbook under `ops/backup/`; ignore local dump/staging artifacts.
- Configure node-exporter's textfile directory and Grafana rules, preserving unrelated changes in the observability README.
- Integrate one pre-deploy backup gate into [deploy.sh](../../scripts/deploy.sh) **before frontend publication and API/Flyway startup**. The current gzip SQL backup runs later and is optional when `PG_DUMP_URL` is missing; do not accidentally retain that downgrade after protection is activated.
- Provision credentials separately from the API `.env`, install/update tooling idempotently and activate only after a verified copy, restore drill and notification test. Keep the existing local deploy backups during transition and document their legacy `.sql.gz` restore format.
- Test synthetic dump/restore, denied access, upload failure, corruption, concurrency, timeouts, missing configuration, budget-state failure, quota boundaries and cleanup. Prove budget checks account for retries/in-flight bytes and never silently permit an upload/deploy on unknown headroom. Validate systemd/calendar/shell/metrics configuration and provider behavior with synthetic R2 test objects only after the zero-cost activation gate passes. Do not store real backups in CI artifacts or Git.
- Record measured size and recovery time, observe a daily run and a pre-deploy run, and prove alert delivery. Disabling the tooling must not delete backup objects or restore application data.
- Update backend/operations documentation and mark the backlog/Oriens item done only after operational acceptance. This draft changes no application behavior and needs no application test-suite run.
- The implementation has security impact: follow [SECURITY_REVIEW.md](../SECURITY_REVIEW.md), including Trail of Bits `differential-review` and regression evidence before opening/marking ready the implementation PR and before merge. No security review is claimed by this spec.

## 8. Confirmed decisions and remaining activation checks

The owner confirmed these choices in the conversation on 2026-11-19:

1. **Deployment failure policy: approved.** Failed/unverified offsite backup blocks deployment, including R2 unavailability or a budget rejection.
2. **Deletion protection: approved.** Enable a **7-day R2 bucket lock** after cost feasibility is established. It protects recent objects against deletion/overwrite, including deliberate operator deletion; bucket administrators can change its rules. Object credentials are not assumed append-only.
3. **Budget: strictly zero additional cost.** No paid overage or billing-risk acceptance was granted. The R2 no-overage feasibility gate above remains unresolved until effective controls are verified; a local 8 GB ceiling must not be presented as a guaranteed zero-cost plan.

Account/bucket location, source egress allowance, actual database permissions/version, recovery credential custody and the monthly drill operator must also be confirmed before activation. These are implementation/preflight checks; no production rollout or paid resource creation is implied by recording the decisions. Do not mark the operational task complete while feasibility or recovery proof remains unresolved.

Technical references: [PostgreSQL dump](https://www.postgresql.org/docs/17/app-pgdump.html), [restore](https://www.postgresql.org/docs/17/app-pgrestore.html), [R2 lifecycle](https://developers.cloudflare.com/r2/buckets/object-lifecycles/), [bucket locks](https://developers.cloudflare.com/r2/buckets/bucket-locks/), [encryption](https://developers.cloudflare.com/r2/reference/data-security/), [tokens](https://developers.cloudflare.com/r2/api/tokens/), [systemd timers](https://www.freedesktop.org/software/systemd/man/latest/systemd.timer.html), [node-exporter textfile collector](https://github.com/prometheus/node_exporter#textfile-collector).
