# Cross-layer consistency review

Approved on 2026-11-15. Review complete request/response flows across sessions and
attendance, member management and finance. Compare frontend schemas, API modules,
types and mutation state with backend DTO validation, service rules and permissions.

## Approach

Trace each candidate from the user action through the trusted service and back to
the rendered state. Prefer this over a DTO-only comparison (which misses behavior)
or building broad new integration infrastructure before investigating.

Fix confirmed inconsistencies at the responsible layer, using existing patterns.
Preserve local work. Do not invent new product rules or change financial accounting
semantics. Record evidence, expected/current behavior, impact, resolution and proof.
Keep sensitive security details in ignored review reports.

## Work items

1. Explore project state, current guides and recent commits (completed).
2. Clarify focus: frontend/backend flows and contracts (completed).
3. Compare approaches (completed).
4. Present scope and obtain approval (completed).
5. Record the approved design (this file).
6. Trace flows, document and fix confirmed findings; run focused regressions and
   the required checks for each changed side. Review security impact and use the
   differential-review workflow for security-relevant changes.

## Validation

Backend changes require `./mvnw test` from `core/`. Frontend changes require
`bun run lint`, `bun run build` and `bun run test` from `client/`. Add regressions
that demonstrate the behavioral mismatch. Report unavailable validation explicitly.

## Completion

Completed. Findings, fixes, regression evidence and final
checks are recorded in [the review](../history/2026-11-15-cross-layer-consistency-review.md).
The approved plan was committed separately as `d5a8dfd`. The fixes, regression
tests and matching guide updates are committed together after review; machine-specific
configuration and local experiments are excluded from version control.
