# Backend guide — Futspring API

Spring Boot REST API in `core/`. Base package: `com.futspring.backend` (`src/main/java/com/futspring/backend`, written `S/` in these documents).

Futspring manages amateur soccer groups ("peladas"): members and admins, match-day sessions ("dailies") with attendance, team sorting, match results, league table, awards, a pelada-wide ranking, per-player stats and a live chat.

These documents are the source of truth for how the API is built. Each one covers one subject, with its rules (do this) and the antipatterns it guards against (don't do that). **Before changing code, read every document listed for your task in [What to read for each task](#what-to-read-for-each-task)**. If the code and a document disagree, point it out instead of silently following either one. When a change alters what a document describes, update that document in the same change.

## Documents

### Foundations

| Document | Contents |
|---|---|
| [stack.md](stack.md) | Libraries and versions, Maven and compose commands, CI |
| [configuration.md](configuration.md) | Environment variables, runtime settings (virtual threads, Hikari, uploads, proxies), secrets, Redis |
| [structure.md](structure.md) | Packages by domain, where a class goes (12-class limit), architectural patterns |
| [conventions.md](conventions.md) | Code conventions, the patterns to copy and the numbered **antipatterns** table |

### Domain

| Document | Contents |
|---|---|
| [domain-model.md](domain-model.md) | Entities and tables, daily status machine, attendance (mensalistas and diaristas), positions, awards |
| [subscriptions.md](subscriptions.md) | Subscription state, read-only lock (402), member cap, Pix claim, notices, prices |
| [finance.md](finance.md) | Pelada finance: transactions, revision and idempotency, permissions, REST contracts, private storage |
| [notifications.md](notifications.md) | Notification e-mails, unsubscribe links, Web Push |

### Rules that cut across domains

| Document | Contents |
|---|---|
| [authorization.md](authorization.md) | The relationship check, permission matrix, rules for new code, known gaps |
| [security.md](security.md) | Filter chain, tokens and refresh sessions, CORS, rate limits, WebSocket, password reset, e-mail verification, uploads, private files, telemetry redaction |
| [error-handling.md](error-handling.md) | Exceptions → HTTP responses, rules for new errors |
| [migrations.md](migrations.md) | Flyway ownership of the schema, migration history, migration rules |
| [performance.md](performance.md) | Fetching, read models, batch commands, transactions, pagination, virtual threads, single-instance assumptions |

### API reference

All routes are under `/api/v1`.

| Document | Routes |
|---|---|
| [api-accounts.md](api-accounts.md) | `/auth`, `/files`, `/users`, account deletion, `/tours`, `/client-errors` |
| [api-peladas.md](api-peladas.md) | `/peladas`, members, invitations and invite links, `/dailies` |
| [api-notifications.md](api-notifications.md) | `/notifications`, preferences, unsubscribe, `/push` |
| [api-billing.md](api-billing.md) | `/platform` (platform admin), `/peladas/{id}/subscription` |

### Operations and quality

| Document | Contents |
|---|---|
| [deploy.md](deploy.md) | CD workflows, nginx, GitHub secrets and variables, database backup |
| [observability.md](observability.md) | Probes, metrics, logs, dashboards, alerts, load test |
| [testing.md](testing.md) | Test kinds, what each area covers, rules for new tests |
| [Security review workflow](../../docs/SECURITY_REVIEW.md) | Mandatory review for PRs with security impact |

## What to read for each task

Always read [structure.md](structure.md) and [conventions.md](conventions.md), then:

| Task | Also read |
|---|---|
| New or changed endpoint / DTO | [authorization.md](authorization.md), [error-handling.md](error-handling.md), the matching `api-*.md`, [testing.md](testing.md), and the frontend [data layer](../../client/docs/data-layer.md) |
| New entity, column or enum value | [domain-model.md](domain-model.md), [migrations.md](migrations.md) |
| New query, read model or batch command | [performance.md](performance.md), [testing.md](testing.md) (query counts) |
| Write to a pelada's sporting or financial data | [subscriptions.md](subscriptions.md) (`requireWritable`), [authorization.md](authorization.md) |
| Attendance, teams, results, finalize | [domain-model.md](domain-model.md) |
| Finance | [finance.md](finance.md) |
| Something that should notify users | [notifications.md](notifications.md), [api-notifications.md](api-notifications.md) |
| Auth, sessions, tokens, WebSocket, uploads, rate limits | [security.md](security.md) |
| Environment variable, secret, Redis | [configuration.md](configuration.md), [deploy.md](deploy.md) |
| Logs, metrics, alerts | [observability.md](observability.md) |
| Deploy, nginx, backups | [deploy.md](deploy.md) |

## Non-negotiable rules

Each rule is detailed in the linked document.

1. **Controller → Service → Repository.** A controller reads the principal, validates the body (`@Valid`) and calls one service method; it never returns an entity ([structure.md](structure.md#architectural-patterns)).
2. **The caller comes from the token**, never from the body or a path parameter; every `{id}` route checks the caller's relationship in the service through `PeladaAccessHelper`, and child ids are scoped to the route's parent ([authorization.md](authorization.md#rules-for-new-code)).
3. **Read-only pelada:** every command that writes sporting or financial data calls `SubscriptionGuard.requireWritable` after its permission check and gets a 402 test ([subscriptions.md](subscriptions.md)).
4. **Expected failures are `AppException`** with a Portuguese message; framework exceptions get a handler in `GlobalExceptionHandler`, not a try/catch ([error-handling.md](error-handling.md)).
5. **Every entity change ships with its Flyway migration**; a migration that ran is never edited; breaking changes go expand/contract ([migrations.md](migrations.md#migration-rules)).
6. **Relations stay `LAZY`**; read paths and batch commands keep a constant number of queries, proven by a `QueryCounter` test comparing a small and a large batch ([performance.md](performance.md), [testing.md](testing.md)).
7. **Lists that grow with time are paginated** with a capped size ([performance.md](performance.md)).
8. **Notifications come from domain events**, written in the publisher's transaction and delivered after commit ([structure.md](structure.md#architectural-patterns)).
9. **Match data changes rebuild aggregates** through `AggregateRebuildService` ([structure.md](structure.md#architectural-patterns)).
10. **Uploads go through `FileUploadService`**; files are served only through `FileReadService` with a relationship check ([security.md](security.md#uploads)).
11. **No e-mail, token, password, Pix key or request body in logs**; log ids ([conventions.md](conventions.md#conventions), [observability.md](observability.md)).
12. **No secrets in tracked files**; everything comes from the environment ([configuration.md](configuration.md)).
13. **A package holds at most 12 classes** (`dto/` aside), with no cycles between a domain's sub-packages; `DomainLayoutTest` enforces it ([structure.md](structure.md#package-layout)).

## PR checklist

- [ ] Code in the right domain package; no package over 12 classes ([structure.md](structure.md)).
- [ ] Controller with one service call; `@Valid` on every `@RequestBody`; response is a DTO record.
- [ ] Caller resolved from the token; relationship checked before any change; child ids scoped to the parent ([authorization.md](authorization.md)).
- [ ] Sporting / financial write calls `requireWritable` and has a 402 case in `SubscriptionReadOnlyTest` ([subscriptions.md](subscriptions.md)).
- [ ] New messages in Portuguese, thrown as `AppException` ([error-handling.md](error-handling.md)).
- [ ] Entity change with its migration; destructive migration noted in the PR with a backup step ([migrations.md](migrations.md)).
- [ ] No lazy access outside a fetch that loaded it; new read path or batch command with a query-count test ([performance.md](performance.md)).
- [ ] Tests: happy path, validation 400, 403 for non-member / non-admin, 404, child id from another pelada ([testing.md](testing.md)).
- [ ] Nothing personal or secret in logs.
- [ ] API contract changed on both sides: DTO and `api-*.md` here, `src/api` + `src/types` + zod schema and the [frontend docs](../../client/docs/README.md); contract snapshots regenerated on purpose only.
- [ ] Every document this change makes wrong updated in the same change; fixed antipatterns / known gaps removed from their "Found in" column.
- [ ] Security impact assessed; [security review workflow](../../docs/SECURITY_REVIEW.md) followed when it applies.
- [ ] `./mvnw test` green in `core/`.
