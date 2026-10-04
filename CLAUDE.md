# Futspring

Manager for amateur soccer groups ("peladas"): members and admins, match-day sessions ("dailies") with attendance, team sorting, results, league table, awards, pelada ranking, player stats and chat.

- `client/` — React 19 + TypeScript + Vite + Tailwind 3 + shadcn/ui (package manager: **bun**)
- `core/` — Spring Boot 3.2 + Java 17 + PostgreSQL, REST under `/api/v1` + STOMP chat on `/ws`
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
