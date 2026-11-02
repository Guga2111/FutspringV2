# Pelada navigation and pages — frontend SPEC

Date: 2026-11-01 · Status: approved for implementation planning

## Goal

Replace the crowded tab-based pelada detail screen with a clear overview and dedicated pages for each main area. Make the current location and path back to the pelada clear, support direct links and browser navigation, and preserve the existing mobile-first app shell.

This SPEC covers frontend structure and behavior only. It does not authorize implementation in this brainstorming round and does not require backend/API changes.

## Approved decisions

- Each main area has its own URL and page.
- Breadcrumb separator is the literal `>` character; do not use `/` or `·` as the visual separator.
- Keep the existing global sidebar. Do not add a second persistent sidebar for pelada areas.
- Add a compact local area selector in the page content. It groups destinations under `Pelada` and `Gestão`; groups are headings in one list, not nested submenus.
- On mobile, the same selector remains the local navigation control and must fit the available width and touch targets.
- Keep pelada settings on its existing dedicated page.
- The overview includes the next session, recent activity, and shortcuts to pelada areas.
- Recent activity includes recent sessions and results. Member changes are represented by an `Em breve` placeholder until a future backend endpoint exists; do not fabricate member activity or add persistence/API work in this frontend scope.
- A page is justified by a distinct purpose and enough content. Small contextual actions and player history may remain dialogs or menus.

## Navigation model

### Breadcrumb

Show a breadcrumb above the page title on every pelada-area page, including settings and session detail where practical:

`Início > [Nome da pelada] > [Área atual]`

For session detail:

`Início > [Nome da pelada] > Sessões > [Data da sessão]`

The current page is plain text, not a link. Ancestors link to their corresponding pages. Use the `>` character as the visual separator. On narrow screens, truncate long pelada names without hiding the current page or making the breadcrumb overflow.

### Local area selector

Place the selector near the breadcrumb/title in the pelada page header. It shows the current destination and opens one list with two labeled groups:

**Pelada**

- Visão geral
- Membros
- Sessões
- Ranking
- Prêmios

**Gestão**

- Financeiro
- Convites (admins only)
- Configurações (admins only)

Selecting an item navigates to its URL. The active destination is clear and exposed accessibly. Do not duplicate these destinations as a nested section in the global sidebar. Do not add submenus in this iteration.

The global sidebar continues to select/open the pelada as it does today. Its active state must cover all descendant pelada routes, not only the overview URL.

## Page map and purpose

| Page | Proposed URL | Primary purpose and content |
|---|---|---|
| Visão geral | `/pelada/:id` | Understand what is happening now and choose the next action. Show next session with existing attendance action and session link; recent sessions/results; a member-changes area marked `Em breve`; shortcuts to the pelada's areas. Do not duplicate full member, ranking, award, finance, or session-history datasets here. |
| Membros | `/pelada/:id/membros` | Find and manage pelada members. Preserve current search, filters, membership type, per-member actions, admin bulk selection/actions, invitation entry point, and member statistics currently shown on cards. |
| Sessões | `/pelada/:id/sessoes` | See the next session and the complete existing session history; admins can create a session. Each row links to that session's existing dedicated detail page. |
| Ranking | `/pelada/:id/ranking` | Review and sort player ranking; preserve player-history access and attendance ranking action. |
| Prêmios | `/pelada/:id/premios` | Review awards and leaders by category using existing awards data. |
| Financeiro | `/pelada/:id/financeiro` | Keep the current FinanceTab capabilities on a dedicated route. |
| Convites | `/pelada/:id/convites` | Keep invitation management on a dedicated route, visible only to admins in navigation. Preserve existing invitation/link management and player invitation behavior. |
| Configurações | `/pelada/:id/settings` | Preserve the current dedicated settings page and its existing permission behavior. |
| Detalhe de sessão | `/daily/:id` | Keep the existing dedicated session page. Add/adjust breadcrumb context to link back to the pelada's Sessões page. |

The exact Portuguese labels above are the proposed UI labels. User-facing text remains pt-BR.

## Content and interaction requirements

### Overview

- Next session is the primary content and retains the existing confirm/withdraw behavior and link to session details.
- Recent sessions/results link to the corresponding session detail pages.
- Member changes display an honest `Em breve` state; no fake records, counts, or dates.
- Shortcuts navigate to each available area. Restricted destinations must not be presented as available to non-admins.
- Admin actions such as creating a session or inviting a player remain available in a context appropriate to their action; they must not be duplicated without purpose.
- Empty, loading, and error states must be explicit. If there is no next session or activity, explain that state and retain useful admin/member actions.

### Dedicated pages

- Move existing area content and interactions without changing their business behavior.
- Use route navigation rather than local tab state so the URL, reload, browser back/forward, and shared links select the same page.
- Preserve query/hash behavior if any existing feature relies on it; do not introduce query parameters for the selected area when the path already expresses it.
- Each page has one clear title and purpose, breadcrumb, local selector, and page-specific loading/empty/error states.
- Keep player history and focused one-off management actions as dialogs/menus where the user benefits from retaining the underlying page context.

## Permissions and security

- Backend authorization remains authoritative. Hiding admin-only navigation is a presentation rule, not a security boundary.
- Convites and Configurações are omitted from the selector and overview shortcuts for non-admins.
- Direct navigation to an admin-only page must retain the existing access-denied behavior; do not rely only on hidden links.
- Existing member access checks and API calls remain unchanged in this frontend reorganization.

## Responsive and visual direction

- Mobile first; preserve the current global sidebar/sheet behavior.
- No second persistent sidebar. The local selector is the only area switcher inside the pelada workspace.
- Breadcrumb, selector, title, actions, and content must fit at 375 px without horizontal overflow. Long pelada names truncate safely.
- Use the existing shadcn components and semantic design tokens; support light and dark themes and accessible keyboard/focus states.
- Visual references: shadcn.io breadcrumb patterns for hierarchical wayfinding and dashboard navigation patterns for a compact breadcrumb/header. These are inspiration for interaction, not a mandate to copy their dashboard chrome.
- Exact spacing, typography, and component styling can be decided during implementation within the existing design system.

## Frontend-only scope and exclusions

Included:

- Route/page decomposition and navigation UI.
- Breadcrumbs and grouped local selector.
- Overview composition from existing frontend data, including the `Em breve` member-changes placeholder.
- Moving existing page content/interactions into dedicated pages while preserving behavior.
- Updating frontend route/navigation documentation as part of implementation.

Excluded:

- Backend endpoint, database schema, migrations, audit/event persistence, or API contract changes for member activity.
- Fabricated member-change activity.
- Reworking the global sidebar architecture or adding pelada sub-navigation to it.
- Redesigning settings behavior or changing permissions/business rules.
- Turning every small action/dialog into a page.

## Implementation acceptance criteria

- Every listed area has a stable, directly loadable URL; refresh and browser back/forward preserve the selected page.
- The breadcrumb uses `>` and links to valid parent destinations; session detail links back to the pelada's Sessões page.
- The grouped selector shows the correct active destination and routes correctly at desktop and mobile widths.
- The global sidebar indicates the active pelada on every descendant route.
- Admin-only destinations are hidden for non-admins and remain protected by existing frontend access states and backend checks.
- Existing members, sessions, ranking, awards, finance, invitations, settings, chat (where currently available), player history, bulk member actions, and attendance actions retain their behavior on the appropriate page.
- Overview contains next session, recent sessions/results, member changes marked `Em breve`, and area shortcuts; it does not show invented data or duplicate complete area datasets.
- Loading, empty, access-denied, and error states remain understandable and usable.
- All pages work in light/dark mode and at 375 px without horizontal overflow, with keyboard-operable selector and visible focus.
- Frontend documentation reflects the new route map and navigation behavior.

## Existing project context

Current routes are `/pelada/:id`, `/pelada/:id/settings`, and `/daily/:id`. The current pelada detail page has tabs for members, sessions, ranking, awards, finance, and admin-only invitations. `usePeladaDetail` already fetches pelada, dailies, ranking, and awards in parallel. Existing settings are already a separate page. The sidebar's pelada active check currently matches only `/pelada/:id` exactly and will need to include descendant routes.

