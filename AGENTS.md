# Futspring

Manager for amateur soccer groups ("peladas"): members and admins, match-day sessions ("dailies") with attendance, team sorting, results, league table, awards, pelada ranking, player stats and chat.

- `client/` — React 19 + TypeScript + Vite + Tailwind 3 + shadcn/ui (package manager: **bun**)
- `core/` — Spring Boot 4.1 + Java 25 + PostgreSQL 17, REST under `/api/v1` + STOMP chat on `/ws`, Actuator on the internal port 9091
- `ops/` — `observability/` (Prometheus + Grafana stack shared by the VPS, its own deploy workflow) and `loadtest/` (local load test of the API image)
- `scripts/deploy.sh` — builds the frontend and the API image and ships them to the VPS; run by the CD workflow on every push to `main` (`.github/workflows/`, see CI/CD in BACKEND.md). Secrets come from GitHub / `.env`, never from the script

## Required reading

The frontend and backend documentation below is imported into every session and MUST be followed. Read both before planning, answering questions about, or changing any code, including changes that seem to touch only one side, since most features cross the API boundary.

@client/docs/FRONTEND.md
@core/docs/BACKEND.md

## Cross-cutting rules

- **The backend is the authority on permissions.** Every route that takes an id checks the caller's relationship (creator / admin / member / self) in the service, and child ids are scoped to the parent in the path. Admin checks in the frontend only hide UI.
- **API contract changes are made on both sides in the same change:** backend DTO/endpoint, frontend `src/api/*` + `src/types/*` + zod schema, and both docs.
- **Schema changes ship with a migration** (see "Database schema & migrations" in BACKEND.md); never rely on `ddl-auto=update` to change an existing column.
- **No secrets in tracked files** (passwords, DB URLs with credentials, JWT secrets). Use environment variables.
- **Design system:** shadcn components and semantic tokens only; no hand-rolled modals/inputs or raw palette colors. Every screen works in light and dark mode and at 375 px wide.
- User-facing text is Portuguese (pt-BR); code, identifiers and docs are English.
- Before finishing: `./mvnw test` in `core/` for backend changes; `bun run lint && bun run build && bun run test` in `client/` for frontend changes.

## Keeping the docs current

These documents are the source of truth for how the project is built. When a change alters anything they describe (endpoints, DTOs, entities, migrations, routes, hooks, services, permissions, security, configuration, conventions, commands), update the matching document in the same change. When you fix an item from an "Antipatterns" table or a "Known gaps" list, remove it from the "Found in" column. If the code and the docs disagree, point it out to the user instead of silently following either one.

## Mandatory security review for pull requests

- Every PR must assess its security impact. Before opening or marking ready a PR with security impact, and before merging it, use **`differential-review` from `trailofbits/skills`**. Read and follow [the security review workflow](docs/SECURITY_REVIEW.md), including its triggers, evidence, remediation and merge requirements. A small diff or a refactor is not an exemption.
- Look for newly introduced vulnerabilities, regressions and **pre-existing vulnerabilities in the changed code and reachable related flows**. Fix confirmed findings in that scope before merge, in this PR or a prerequisite fix PR; do not hide them as unrelated technical debt. Record unrelated findings separately with ownership and priority; critical/high findings block the affected release until fixed.
- Review the final code against the current target branch, rerun affected analysis after fixes or relevant base/head changes, and include a sanitized summary, reviewed commit IDs, regression-test results and report reference in the PR. CI success alone does not satisfy this requirement. If the skill or required validation is unavailable, state the blocker; never claim the review passed.
- **`security-audit` from `cloudflare/security-audit-skill`** complements this workflow for explicit broader audits. It does not replace the mandatory Trail of Bits review. Installing either skill does not itself perform an audit.
