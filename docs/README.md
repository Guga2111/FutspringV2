# Documentation

## Current references

| Document | Purpose |
| --- | --- |
| [Frontend guide](../client/docs/README.md) | Index of the client documents: setup, structure, routes, data layer, hooks, design system, forms, PWA, testing, patterns and antipatterns |
| [Backend guide](../core/docs/README.md) | Index of the API documents: setup, configuration, package layout, domain, authorization, security, migrations, API reference, operations, testing, patterns and antipatterns |
| [Product record](../PRODUCT.md) | Users, purpose, positioning, terminology and principles, read by the design skills (product truth only; the visual system is in the frontend's design-system.md) |
| [Agent workflow and skills](agent-workflow.md) | From request to merge: spec, research, design, browser validation, and which installed skill to use at each step |
| [Security review workflow](SECURITY_REVIEW.md) | Required review and remediation process for security-relevant PRs |
| [Observability](../ops/observability/README.md) | Operating the shared Prometheus, Grafana and Loki stack |
| [Landing photos](../client/public/landing/README.md) | Asset names, formats and usage |
| [Backlog](BACKLOG.md) | Unscheduled ideas; not release requirements |

Each guide is an index of small topic documents (one subject each, with its rules and antipatterns) plus the non-negotiable rules and the PR checklist of its side. The guides describe current behavior.
Records written before 2026-11-22 cite `FRONTEND.md` / `BACKEND.md` and their section names: those sections now live, under the same headings, in the topic documents the indexes list. If a proposal or an
old report disagrees with them, inspect the implementation before acting.

## Open planning and acceptance

- [Message import](plans/message-import-implementation-plan.md): unimplemented
  performance and waiting-feedback proposal, preserved from the local draft.
- [Runtime upgrade](plans/2026-10-29-java25-boot4-virtual-threads-design.md): retained
  for the load-test comparison and explicitly pending production acceptance.
  Historical setup and rollout instructions are not the current runbook.
- [Database backup to Cloudflare R2](plans/2026-11-19-r2-database-backup-spec.md): the timer is
  active; the deploy transition and the ongoing budget checks are still pending. The
  operational guide is [ops/backup](../ops/backup/README.md).

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
| [Mobile redesign (Jakob's law)](plans/2026-11-16-mobile-jakob-redesign-spec.md) | Implemented; rationale for the mobile shell, referenced by the frontend [navigation guide](../client/docs/navigation.md) |
| [Floating mobile navigation](plans/2026-11-17-floating-mobile-navigation-design.md) | Implemented; direction contract of the bottom tab capsule, referenced by the navigation guide |
| [F-13 large services split](plans/2026-11-15-f13-large-services-design.md) | Approved responsibility split of the daily-results and pelada services; integrated in one PR to `dev` |
| [Legacy forms refactor](plans/2026-11-15-legacy-forms-refactor-design.md) | Implemented; why the three legacy forms moved to react-hook-form + zod + `Field` (the rules live in [forms.md](../client/docs/forms.md)) |
| [Cross-layer consistency plan](plans/2026-11-15-cross-layer-consistency-design.md) | Approved scope and method of the review whose results are in [the review](history/2026-11-15-cross-layer-consistency-review.md) |
| [Landing pricing section](plans/2026-11-22-landing-pricing-design.md) | Implemented; what the landing publishes about prices and why (prices come from `SubscriptionPlans.java`) |
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
