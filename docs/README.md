# Documentation

## Current references

| Document | Purpose |
| --- | --- |
| [Frontend](../client/docs/FRONTEND.md) | Client setup, routes, API modules, hooks, forms, design system and checks |
| [Backend](../core/docs/BACKEND.md) | API setup, configuration, contracts, permissions, migrations, deployment and checks |
| [Security review workflow](SECURITY_REVIEW.md) | Required review and remediation process for security-relevant PRs |
| [Observability](../ops/observability/README.md) | Operating the shared Prometheus, Grafana and Loki stack |
| [Landing photos](../client/public/landing/README.md) | Asset names, formats and usage |
| [Backlog](BACKLOG.md) | Unscheduled ideas; not release requirements |

The frontend and backend guides describe current behavior. If a proposal or an
old report disagrees with them, inspect the implementation before acting.

## Open planning and acceptance

- [Message import](plans/message-import-implementation-plan.md): unimplemented
  performance and waiting-feedback proposal, preserved from the local draft.
- [Runtime upgrade](plans/2026-10-29-java25-boot4-virtual-threads-design.md): retained
  for the load-test comparison and explicitly pending production acceptance.
  Historical setup and rollout instructions are not the current runbook.

## Design rationale and release evidence

These records explain past decisions and validation. Their dates, branch names
and test counts describe those changes, not a new release approval.
Completed rollout and review records live in `history/`. The Sport-Tech and
subscription specs retain their original paths because source comments and
migration V19 reference them.

| Record | Reason retained |
| --- | --- |
| [Sport-Tech design](plans/2026-11-01-design-system-sport-tech-spec.md) | Token rationale still referenced by the client styles and contrast utilities |
| [Subscriptions](plans/2026-11-07-subscriptions-platform-admin-spec.md) | Business-rule rationale referenced by migration V19 and the backend guide |
| [Launch hardening](history/2026-11-11-launch-hardening-spec.md) | Incremental rollout and validation evidence for session and observability changes |
| [Privacy hardening](history/2026-11-13-privacy-hardening.md) | Verification, private photos, telemetry and email-preference link changes |
| [Release P1](history/2026-11-13-release-p1-fixes.md) | Account identity, optimistic locking and refresh/logout regression evidence |
| [Release P2](history/2026-11-13-release-p2-fixes.md) | Session replacement and private-photo regression evidence |
| [STOMP expiration](history/2026-11-13-stomp-jwt-expiration-design.md) | Connection lifetime invariants and regression evidence |
| [Cross-layer consistency review](history/2026-11-15-cross-layer-consistency-review.md) | Session navigation, load-error and pelada-calendar fixes with regression evidence |
| [Quick payment report](history/2026-11-14-finance-quick-report-design.md) | Latest payment-report flow and its validation evidence |
| [Load-test results](../ops/loadtest/results) | Local measurements retained with their limitations |

## Historical reviews

- [Known issues from October](history/known-issues.md): resolved findings and original evidence.
- [Security review from November 6](history/security-review-2026-11-06.md): historical findings;
  some statuses predate the subsequent hardening work.
- [Performance review from November 6](history/performance-review-2026-11-06.md): historical
  hypotheses; recommendations require revalidation against current behavior.

These reports are not current issue trackers or proof of release readiness. New
security reviews belong in the ignored `.security-reviews/` directory or outside
the checkout, following the security review workflow.

## Maintenance

- Update the relevant current guide in the same change as the implementation.
- Use `plans/` for concrete pending work. After implementation, incorporate the
  lasting contracts and conventions into the current guides and remove redundant
  plans. Retain a record only when it carries useful rationale, unresolved
  acceptance or validation evidence; list its purpose here. Move completed
  rollout and review evidence into `history/`; keep paths referenced by immutable
  migrations stable.
- Keep one-off prompts, exported prototypes and manually generated diagrams out
  of the checkout. The Flyway migrations and mapped entities define the schema;
  database documentation must have an owner and a reproducible update process.
- Keep private research and local experiments out of version control.
