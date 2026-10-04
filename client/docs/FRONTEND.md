# Frontend Documentation — Futspring Client

React single-page app in `client/`. UI text is in Portuguese (pt-BR); code identifiers are in English.

## Stack

- React 19, TypeScript ~5.9 (strict), Vite 8, Vitest 2
- **bun** as package manager — never use `npm`/`yarn`; `bun.lock` is the only lockfile
- Tailwind CSS 3.4 (`tailwind.config.js` + `postcss.config.js`, `tailwindcss-animate`), class-based dark mode
- shadcn/ui (style `default`, base color `slate`, CSS variables) on top of Radix primitives; components in `src/components/ui`
- Icons: **lucide-react only**
- React Router 7 (declarative `BrowserRouter` + `<Routes>`), Axios, Sonner (toasts)
- Charts: recharts through shadcn `ui/chart.tsx`; tables: `@tanstack/react-table` v8 + shadcn `Table`
- `date-fns`, `react-day-picker` (shadcn `Calendar`), `cmdk` (shadcn `Command`), `vaul` (shadcn `Drawer`)
- Realtime chat: `@stomp/stompjs` + `sockjs-client`
- Forms (see [Forms](#forms-mandatory)): `react-hook-form` + `zod` 4 + `@hookform/resolvers` + shadcn `Field`
- No server-state library (no react-query): data is fetched in hooks with `useState`/`useEffect`

## Commands

Run from `client/`:

```bash
bun install
bun run dev          # Vite dev server on http://localhost:5173 (proxies /ws to :8080)
bun run build        # tsc -b && vite build
bun run lint         # ESLint 9 (flat config)
bun run test         # vitest run
bunx shadcn@latest add <component>   # add a shadcn component to src/components/ui
```

CI (`.github/workflows/ci-frontend.yml`) runs `bun install --frozen-lockfile`, lint, build and test on pushes and PRs to `main`/`dev` that touch `client/`; the deploy on `main` waits for it (see CI/CD in `BACKEND.md`). Lint is blocking (0 errors; one `react-hooks/incompatible-library` warning from TanStack Table remains). Before finishing a change run `bun run lint && bun run build && bun run test`.

## Environment

- `VITE_API_URL` — backend base URL, read at build time (production: `https://futspring.luisgosampaio.com/api` — nginx strips `/api` before proxying, so requests go to `/api/api/v1/...`; locally it is unset). Falls back to `http://localhost:8080`. The only place that reads it is `src/lib/config.ts` (`API_BASE`), used by `api/client.ts` and `getFileUrl`.
- The chat opens `new SockJS("/ws")` (relative, `components/pelada/hooks/usePeladaChat.ts`): it works through the Vite dev proxy (`vite.config.ts`) and in production because the app and the API share an origin behind the reverse proxy.
- `vite.config.ts` defines `global: 'globalThis'` (needed by sockjs-client).
- The backend's CORS allows `ALLOWED_ORIGINS` (default `http://localhost:5173`), so keep the dev server on that port or set `ALLOWED_ORIGINS` for the API.
- The chat needs a valid token (CONNECT without one is rejected) and only members can subscribe to a pelada's topic; send errors arrive on `/user/queue/errors`.

## Folder structure

```
client/
├── docs/                 this file
├── public/               static files (gerrard.png logo/favicon, pele.jpg, ronaldo.jpg)
└── src/
    ├── main.tsx          applies the saved theme, StrictMode > BrowserRouter > AuthProvider > App + <Toaster position="top-center"/> (bottom-right would cover the chat button)
    ├── App.tsx           routes, React.lazy pages, one <Suspense>, an <ErrorBoundary> per route
    ├── index.css         Tailwind layers, design tokens (:root / .dark), brand gradient classes, landing animations
    ├── api/              one module per backend domain; typed async functions returning response.data
    │   ├── client.ts     the axios instance (interceptors are added by AuthContext)
    │   ├── auth.ts, peladas.ts, dailies.ts, users.ts, chat.ts
    │   └── *.test.ts     vitest tests for api modules
    ├── components/
    │   ├── ui/           shadcn primitives (owned code, see shadcn rules)
    │   ├── ConfirmActionDialog.tsx   shared AlertDialog for confirmations
    │   ├── pelada/       pelada detail feature (PeladaBanner, MembersGrid + MemberCard, NextSessionCard, SessionHistoryList, RankingTable, AwardsTab, PeladaChat + ChatPanel, dialogs…)
    │   │   └── hooks/    usePeladaDetail, usePeladaActions, usePeladaChat, usePlayerPeladaHistory, useComparePlayers, useUserSearch
    │   ├── daily/        session detail feature (DailyHeader with the admin actions, AttendanceSummaryCard, AttendanceList, TeamsSection + TeamCard/TeamColorDot, LiveSessionCard, LiveLeagueTable, SavedMatchesList, LiveTeamsSection, ResultsDialog + ResultsMatchCard + ScoreStepper, ChampionHero, DailyAwardsGrid, FinishedSessionTabs, DailyStatusBadge, modals…)
    │   │   └── hooks/    useDailyDetail, useDailyActions, useDailyModals, useResultsForm, usePlayerSelection
    │   ├── profile/      profile feature (KpiCard, charts, MatchHistoryTable, EditProfileModal…) + index.ts barrel
    │   │   └── hooks/    useProfile
    │   ├── layout/       AppLayout (shell + MyPeladasContext provider), AppSidebar, SidebarPeladaItem, UserMenu, MobileTopBar
    │   ├── PeladaAvatar.tsx   pelada photo or gradient with initials (sidebar, cards, banner)
    │   ├── PlayerAvatar.tsx, StarRow.tsx   round player avatar (photo / initials, `colorId` for per-player colors) and ★ rating
    │   ├── PrivateRoute.tsx, ErrorBoundary.tsx, ScrollToTop.tsx
    │   └── CreatePeladaModal.tsx, EditPeladaModal.tsx   Sheet-based forms
    ├── context/          AuthContext.tsx (provider, token storage, axios interceptors), auth-context-value.ts, my-peladas-context-value.ts
    ├── hooks/            useAuth, useMyPeladas (+ useMyPeladasContext), useTheme (dark class + localStorage.theme), useIsMobile (< 768 px)
    ├── lib/
    │   ├── config.ts     API_BASE (the only reader of VITE_API_URL)
    │   ├── utils.ts      cn(), getFileUrl(), getInitials(), getPeladaGradient()
    │   ├── errors.ts     getErrorMessage(), getErrorStatus(), getApiErrorBody()
    │   ├── form-errors.ts applyServerErrors() for react-hook-form
    │   └── constants.ts  DAYS_OF_WEEK (API value + pt-BR label), dayOfWeekLabel()
    ├── pages/            LandingPage, AuthPage, HomePage, PeladaDetailPage, DailyDetailPage, ProfilePage, NotFoundPage
    ├── schemas/          zod schemas mirroring request DTOs: daily.ts, user.ts, upload.ts
    ├── types/            API DTO types: auth, pelada, daily (DailyStatus), stats, user (PublicUser, Position), chat
    └── utils/            pure functions: matchStats, matchPlayers, parseSessionMessage (WhatsApp text → teams/matches), dates (local date parsing, short pt-BR labels), liveSession (player totals, scorers line, matchup rotation, goal check), attendance (confirmed/pending split, sort rule and hints), finishedSession (champion summary, match stat names, player sort, award detail), sessions (next session, month groups, pt-BR session labels), memberFilters (position normalization, counts, search)
```

Where new code goes:
- A feature's components go in `components/<feature>/`, its hooks in `components/<feature>/hooks/`. Hooks used by more than one feature go in `src/hooks/`.
- Pure helpers (no React) go in `src/utils/` or `src/lib/`; form schemas in `src/schemas/<domain>.ts`.
- Import with the `@/` alias (`@/` → `src/`, configured in `tsconfig*.json`, `vite.config.ts`, `vitest.config.ts`). There are no `../` imports; don't add any.

## Routing (`App.tsx`)

| Path | Page | Access |
|------|------|--------|
| `/` | `LandingPage` (redirects to `/home` when logged in) | public |
| `/auth` (`?tab=login\|register`) | `AuthPage` — returns to `location.state.from` after login | public |
| `/home` | `HomePage` — the user's peladas and next sessions | private |
| `/pelada/:id` | `PeladaDetailPage` — banner card, pill tabs (members grid with search and position chips, stats from the ranking already loaded; sessions: next-session card with confirm/withdraw + history grouped by month; ranking; awards with the leader highlighted), chat from a floating button (Popover panel on desktop, Drawer on mobile). Ranking rows have a history button and the ⌘K menu has "Histórico do Jogador" (⌘I); both open `PlayerHistoryDialog` (lazy-loaded: summary, goals/assists chart, sessions linking to `/daily/:id`) | private |
| `/daily/:id` | `DailyDetailPage` — header (back link, date, status, metadata, admin actions + ⋯ menu); before the session: attendance card (confirm / withdraw), collapsible attendance list (confirmed / pending, admin confirm/remove/confirm all) and teams (sort, swap, rename, color); live: live card (finalize / lançar resultados), live league table, saved matches, teams with goals and assists; the results dialog (Dialog on desktop, Drawer on mobile: team chips, score steppers, goals/assists with a goal check that only warns); finished: champion photo + champion team card, awards, pill tabs (final table, match cards, sortable players table, teams with final position) | private |
| `/profile/:id` | `ProfilePage` — KPIs, charts, match history, peladas in common, edit profile (own). Another user's profile is only visible when you share a pelada (403 → message) | private |
| `*` | `NotFoundPage` | public |

- Every page is `React.lazy` with a default export. Public routes: `<ErrorBoundary>` → page. Private routes are children of one **layout route** whose element is `<ErrorBoundary>` → `<PrivateRoute>` → `<AppLayout>`; each child is `<ErrorBoundary>` → page.
- `AppLayout` (`components/layout/`) is the shell: `SidebarProvider` + `AppSidebar` (logo, "Início", "Minhas peladas" with the "+" that opens `CreatePeladaModal`, `UserMenu` with Perfil / Notificações "Em breve" / Tema / Sair) + `SidebarInset` with `MobileTopBar` and the page in `<Suspense><Outlet/></Suspense>`, so the shell stays on screen while a page loads. Sidebar: 264 px, collapsible to 64 px (state in `localStorage.sidebar_open`); below 768 px it is a Sheet opened from the top bar that closes when you pick a pelada.
- `AppLayout` loads the user's peladas once (`useMyPeladas`) and shares them through `MyPeladasContext` (`useMyPeladasContext()`: `peladas`, `loading`, `error`, `reload`, `updateNextDaily`, `openCreatePelada`), used by the sidebar and the Home page.
- `PrivateRoute` only checks that a token exists (no expiry check); an expired token is caught by the 401 interceptor.
- Pages don't render a nav bar; they own their padding and use the `.page-enter` fade on their root.
- To add a private page: create `pages/<Name>Page.tsx` (default export), add the lazy import and a child `<Route>` under the layout route in `App.tsx`, and link to it from the sidebar or the page that leads to it.

## Data layer

### API client and auth

- `src/api/client.ts` exports the single axios instance (`apiClient`). **Only `src/api/*` imports it**; never import `axios` or call `fetch` elsewhere.
- `context/AuthContext.tsx` owns the session: token in `localStorage.futspring_token`, user in `localStorage.futspring_user`, `login(token, user)` / `logout()`, read through `useAuth()`. Nothing else touches those keys.
- AuthContext registers the interceptors: the request interceptor (registered when `AuthContext.tsx` loads, so the shell's first requests already carry it) adds `Authorization: Bearer <token>`; the response interceptor (in the provider's effect) logs out on **401** and hard-redirects to `/auth`, except for `/api/v1/auth/*` requests (a wrong password is a 401 the login form shows). There is no refresh token (the JWT lasts 7 days).
- The chat sends the same token in the STOMP `connectHeaders`.

### API modules (`src/api/*.ts`)

Each module exports typed async functions that return `response.data`, plus the request payload types (`CreatePeladaData`, `MatchResultInput`, `PopulateDailyInput`…). Response types live in `src/types/` and mirror the backend DTOs in `core/docs/BACKEND.md`.

| File | Endpoints |
|------|-----------|
| `auth.ts` | `/api/v1/auth/register`, `/api/v1/auth/login` |
| `peladas.ts` | `/api/v1/peladas` (my, detail, create, update, delete, image, players, admin, ranking, awards, member stats, member history), `/api/v1/users/search` (`PublicUser`, q ≥ 3 chars) |
| `dailies.ts` | `/api/v1/peladas/{id}/dailies`, `/api/v1/dailies/{id}` (detail, confirm, admin confirm, confirm all, sort/swap teams, team name/color, status, results, finalize, populate, champion image, delete) |
| `users.ts` | `/api/v1/users/{id}` (profile, update, image, background, stats, timeline, matches, peladas in common) |
| `chat.ts` | `/api/v1/peladas/{id}/messages` (paged history) |

When an endpoint or DTO changes, update the module, `src/types/*` and this table in the same change as the backend.

### Hooks and async state

Data flows **api → hook → page/component**. A hook owns the request state; the page renders it.

- `usePeladaDetail(id)` — loads pelada, dailies, ranking and awards in parallel, each with its own loading flag; exposes `accessDenied` (403), `error`, `refetch*` (each returns its promise) and `mergeDaily(item)` to merge a mutation result into the sessions list. Results are keyed by pelada id and responses for another pelada are dropped, so navigating between peladas shows the skeletons, never the previous pelada's data.
- `usePeladaActions(pelada, refetchPelada, mergeDaily)` — delete pelada, remove member, toggle admin, and confirm/withdraw the caller in the next session (merged into the sessions list and the sidebar through `updateNextDaily`).
- `usePeladaChat(peladaId, token)` — chat history + STOMP connection with backoff, `send`, error queue toasts.
- `useMyPeladas()` (in `src/hooks/`, called once by `AppLayout`; read it with `useMyPeladasContext()`) — the sidebar and home list (one request; each item has `isAdmin` and `nextDaily` with the caller's attendance).
- `useUserSearch(query)` — debounced search, keyed by query, `tooShort` below 3 characters.
- `useComparePlayers(peladaId, a, b)` — profile + pelada stats of two players, keyed by the pair.
- `useProfile(userId)` — profile page data keyed by user id; `status` is `loading | ready | forbidden | notFound | error`.
- `useDailyDetail(id)` — loads the daily detail; exposes `setDaily` to merge a mutation's response and `refetch` (returns a promise, doesn't show the skeleton again).
- `useDailyActions({ daily, setDaily, refetch })` — every mutation of the session page with its pending flag.
- `useDailyModals` — open/close state of the daily page's dialogs, plus `attendanceOpen` (null = open until the teams are sorted; the page sets it to false after a successful sort).
- `useResultsForm(daily, mode, onSaved)` — the results dialog's react-hook-form + `useFieldArray` (`makeResultsSchema(teams)` in `schemas/daily.ts`). `mode` `add` (live session: new matches, numbered after the saved ones, with the next matchup suggested by `suggestPairing`) or `edit` (every saved match). It always sends the full list: the backend treats it as the session's full set of matches and deletes saved matches that were left out, so `add` sends the saved matches too. Reloads the detail after saving (league table and stats are recomputed server-side).
- `usePlayerSelection` — shared multi-select of players (finalize and results modals).
- `usePlayerPeladaHistory(peladaId, userId | null, limit | null)` — loads `getPlayerPeladaHistory` (`?limit=`, server-side) for the player history dialog; returns `{ rows, totalSessions, loading, fetching, error, retry }` (rows newest first). `loading` is only true until the first response for that player; changing the limit keeps the previous rows on screen with `fetching` (the dialog dims them). The dialog's period selector (last 5 / 10 / 20 / all, default 5) sets the limit, and the summary tiles, chart and table are derived from the returned rows. Results are keyed by request (no `setState` in the effect body), so switching player never shows the previous player's data. Reference for new fetch hooks under the `react-hooks/set-state-in-effect` lint rule.

Mutations: `try { await api…; toast.success(…) } catch (error) { toast.error(getErrorMessage(error, "…")) }`, keep an `isPending` flag that disables the button, then either merge the returned DTO into state (`setDaily`) or refetch. Prefer merging when the endpoint returns the updated resource.

## Auth & permissions in the UI

- Admin checks in the client only decide what is **shown**. The backend enforces every rule (see "Ownership & authorization" in `BACKEND.md`); never rely on hiding a button for security.
- Pelada admin is derived in `PeladaDetailPage` from `pelada.members[].isAdmin` / `creatorId`. Session admin uses the server-provided `daily.isAdmin`; prefer server-provided flags when they exist.
- A 403 from a detail endpoint renders an "access denied" state (`accessDenied` in `usePeladaDetail`/`useDailyDetail`), not a toast loop.
- Team color can be edited by a team player or an admin (`TeamsSection`: `canEditColor = isOnTeam || daily.isAdmin`), mirroring the backend rule.

## Types

- `src/types/*` — API response DTOs, matching the backend field names exactly.
- `src/api/*` — request payload types next to the function that sends them.
- Component prop types and view models stay next to the component/hook that owns them.
- Status values use a union type and typed maps (`types/daily.ts`, `DailyStatusBadge`); do the same for new enum-like fields:

```ts
export type DailyStatus = "SCHEDULED" | "CONFIRMED" | "IN_COURSE" | "FINISHED" | "CANCELED"

export const dailyStatusLabel: Record<DailyStatus, string> = {
  SCHEDULED: "Agendada",
  CONFIRMED: "Confirmada",
  IN_COURSE: "Em andamento",
  FINISHED: "Finalizada",
  CANCELED: "Cancelada",
}
```

`Record<Enum, …>` makes TypeScript fail when a value is added, instead of `switch`/`if` chains spread across components.

## Styling & design system

- **Tokens:** CSS variables in `src/index.css` as bare HSL triplets (`--primary: 222.2 47.4% 11.2%`) for `:root` (light, stock slate) and `.dark` (custom neutral grays; the redesign handoff in `docs/design/` uses slightly darker hex values, which were not adopted for the base tokens), exposed through `tailwind.config.js` as `hsl(var(--x))`. Use the semantic classes: `bg-background`, `bg-card`, `text-foreground`, `text-muted-foreground`, `border-border`, `bg-primary`, `text-destructive`, `bg-accent`…
- **Redesign tokens** (light + dark, same names):

  | Class | Use |
  |-------|-----|
  | `bg-card-elevated` | table headers, cards inside cards |
  | `bg-row-hover` | list/table row hover |
  | `bg-avatar-fallback`, `bg-avatar-1…6` | initials avatars (neutral / colored by id) |
  | `text-subtle-foreground`, `text-faint-foreground` | tertiary text and uppercase labels |
  | `border-team-dot-border` | ring around team color dots |
  | `success` (`DEFAULT`, `strong`, `soft`, `muted`, `border`), `warning`, `info` (`DEFAULT`, `muted`) | feedback, "Você vai", Garçom |
  | `destructive-soft`, `destructive-muted` | destructive hover text/background |
  | `gold`, `gold-muted`, `silver`, `bronze` | 1st/2nd/3rd, champion row |
  | `status-scheduled*`, `status-live*` | status badges (`status-live` is also the "Admin" pill) |
  | `live-foreground`, `live-border` + `.bg-gradient-live` | live session card |

  Composite classes in `index.css`: `.bg-gradient-date`, `.bg-gradient-leader`, photo overlays (`.bg-banner-overlay`, `.bg-photo-overlay`, `.bg-hero-overlay`, the same in both themes because they sit on images) and `.scrollbar-none`. Radii `rounded-tile` (14px) and `rounded-hero` (18px); shadows `shadow-menu`, `shadow-panel`, `shadow-dialog`, `shadow-fab` (overlays only).
- **Status maps:** `dailyStatusLabel` and `dailyStatusStyle` (badge + dot classes) in `types/daily.ts`, rendered by `DailyStatusBadge` (`size` `sm` | `md` | `lg`, Badge `variant="status"`).
- **Brand green** (`#15803d` → `#16a34a`) lives only in the `.bg-gradient-primary`, `.text-gradient-primary` and `.checkbox-gradient` classes in `index.css`. Use those classes or the Button `variant="gradient"`; never retype the hex. If a solid brand color is needed, add a `--brand` token (light + dark) to `index.css` and `tailwind.config.js` first.
- **Chart colors:** `--chart-1` (green) and `--chart-2` (blue), light and dark, exposed as `chart-1`/`chart-2` in Tailwind. `--gold` (`text-gold`) is for stars, trophies and crowns. In a `ChartConfig` use `color: "hsl(var(--chart-1))"` and reference series as `var(--color-<key>)` (reference: `components/pelada/PlayerHistoryDialog.tsx`).
- **New colors** (status badges, positions, more chart series): add tokens to both `:root` and `.dark` (e.g. `--chart-1…5`, `--success`) and use them; don't scatter `bg-green-100 text-green-800` maps without dark variants.
- **Dark mode** is class-based (`.dark` on `<html>`); the choice is saved in `localStorage.theme`, applied in `main.tsx` and toggled from the user menu (`useTheme`). Every new UI must work in both themes.
- **Composition:** `cn()` from `@/lib/utils` for class names; `cva` variants for repeated styles (`components/ui/button-variants.ts` has `default`, `destructive`, `outline`, `secondary`, `ghost`, `link`, `gradient`).
- **Overlays:** `Sheet` for create/edit forms (reference: `CreatePeladaModal`, `EditPeladaModal`), `Dialog`/`AlertDialog` for confirmations and short forms, `Drawer` for mobile bottom panels (chat), `DropdownMenu` / `Command` for menus.
- **Responsive:** mobile first; the redesign switches to the mobile layout below **768 px (`md:`)**, which is also the shadcn sidebar's `useIsMobile` breakpoint; use `md:` for new layouts. Older screens still use `sm:` and `lg:` for side panels (chat sidebar). Grids go `grid-cols-1 md:grid-cols-2 lg:grid-cols-3`. Check every screen at 375 px.
- Buttons are pill-shaped (`rounded-full` in the base variant); don't override the radius per screen.

### shadcn/ui usage rules (mandatory)

Before UI work, load the `shadcn` skill. Run its CLI from `client/` (where `components.json` lives).

**1. Scope.** Every element in these categories must use the matching component from `@/components/ui`:

| Category | Components |
|----------|-----------|
| Actions | `Button` (icon-only: `size="icon"` + `aria-label`; links styled as buttons: `<Button asChild><Link/></Button>`) |
| Form fields | `Input`, `Textarea`, `Select`, `Checkbox`, `Calendar`/`Popover` for dates |
| Form structure | `Field`, `FieldLabel`, `FieldError`, `FieldGroup`, `FieldSet`/`FieldLegend` |
| Overlays | `Dialog`, `AlertDialog`, `Sheet`, `Drawer`, `Popover`, `DropdownMenu`, `Command`, `Tooltip` |
| Disclosure / navigation | `Tabs`, `Collapsible`, `Accordion` |
| Feedback / status | `Skeleton`, `Badge`, `Progress`, `Alert`, toasts via `sonner` |
| Data display | `Card`, `Table`, `Avatar`, `Separator`, `chart` |

Installed today: alert, alert-dialog, avatar, badge, button, calendar, card, chart, checkbox, collapsible, command, dialog, drawer, dropdown-menu, field, input, label, popover, select, separator, sheet, sidebar, skeleton, table, tabs, textarea, toggle, toggle-group, tooltip, progress (`indicatorClassName`, e.g. `bg-gradient-primary`). Redesign variants: `Tabs` `variant="pill"` (on `TabsList` and `TabsTrigger`; full-width equal tabs below md) in `tabs-variants.ts`, toggle `variant="chip" size="chip"` (filter chips) and `variant="team"` (results dialog team picker) in `toggle-variants.ts`, Badge `variant="status"`. `sidebar.tsx` was adapted: 264/64 px widths, no cookie (AppLayout persists the state), `useSidebar`/context in `sidebar-context.ts`, `useIsMobile` from `src/hooks/useIsMobile.ts`; `SheetContent` takes an `overlayClassName`. `field.tsx` was adapted to Tailwind 3 (no container-query orientation). Variants live in `button-variants.ts` / `badge-variants.ts` so the component files only export components.

Not in scope: layout and text elements (`div`, `section`, `main`, `header`, `h1`–`h6`, `p`, `ul`, `img`) and the native `<form>`. Keep those as semantic HTML styled with tokens.

**2. Missing component → add it, don't hand-roll it.** Run `bunx shadcn@latest add <name> --dry-run` first: the registry may want to overwrite existing files (answer "no" unless you mean to update them). This project is on Tailwind 3; if a generated file uses Tailwind 4-only syntax, adapt it before committing.

**3. Customize without forking.** Use `className` (merged with `cn()`) and `variant`/`size`. A style that repeats becomes a new `cva` variant in the `components/ui` file. Don't copy a component's markup into a feature.

**4. `components/ui` is owned code.** Changes affect every screen: keep them backward compatible (add variants, don't change defaults) and check the other usages.

**5. Escape hatch.** A custom interactive component is allowed only when shadcn has no equivalent; build it from Radix/shadcn pieces in `components/<feature>/` with a one-line comment explaining why, and tell the user.

## Forms (mandatory)

Every new form, and every existing form you substantially change, uses **react-hook-form + a zod schema + shadcn `Field`**. Don't validate with `useState`, hand-written `validate()` functions or `FieldErrors` objects. References: `CreateSessionDialog` (`schemas/daily.ts`, zod input/output types for a nullable date) and `EditProfileModal` (`schemas/user.ts`, `schemas/upload.ts`). Still on `useState`: `AuthPage`, `CreatePeladaModal`, `EditPeladaModal`, `FinalizeModal`, `ImportFromMessageModal`; migrate them as they are touched. `ResultsDialog` (`useResultsForm`) is the reference for a form with a field array.

Rules:

1. **One schema per form in `src/schemas/<domain>.ts`**, copying every constraint of the backend request DTO (`@NotBlank` → `.min(1)`, `@Size(min = 3, max = 30)` → `.min(3).max(30)`, `@Min(0)` → `.min(0)`, `@Email` → `.email()`), messages in Portuguese, with a comment naming the DTO it mirrors. Derive the type with `z.infer`.
2. **Keep schemas in sync with the backend**: a DTO change updates its schema in the same change.
3. **`useForm` + `zodResolver(schema)`** with complete `defaultValues` (never `undefined`).
4. **Fields go through `Controller` + `Field`**: `data-invalid` on `Field`, `aria-invalid` on the control, `FieldLabel htmlFor` matching the control `id`, `FieldError` for the message, correct `type`/`autoComplete`.
5. **Native `<form onSubmit={form.handleSubmit(onSubmit)} noValidate>`**, submit `Button type="submit" disabled={form.formState.isSubmitting}`. Use `formState.isDirty`/`isSubmitting` and `form.reset(values)` instead of extra `isLoading`/`hasChanges` state.
6. **Map backend errors**: the validation 400 body is `{status, message, timestamp, errors: {field: message}}`; other errors are `{status, message, timestamp}`. Put field errors on the fields and the rest on `root`, shown in an `Alert variant="destructive"`, with `applyServerErrors(form, error, fallback)` (`lib/form-errors.ts`); outside forms use `getErrorMessage(error, fallback)` (`lib/errors.ts`) — never per-component casts.
7. **File inputs** validate size (5 MB) and type (`image/jpeg`, `image/png`, `image/webp`) with `schemas/upload.ts` (`validateImageFile`, `IMAGE_ACCEPT`), matching `FileUploadService`.

```ts
// src/schemas/user.ts
import { z } from "zod"

// mirrors UpdateProfileRequest
export const profileSchema = z.object({
  username: z.string().trim().min(3, "Mínimo de 3 caracteres").max(30, "Máximo de 30 caracteres"),
  position: z.enum(["GOLEIRO", "ZAGUEIRO", "MEIO", "ATACANTE"]),
  stars: z.number().int().min(1).max(5),
})
export type ProfileValues = z.infer<typeof profileSchema>
```

```tsx
const form = useForm<ProfileValues>({
  resolver: zodResolver(profileSchema),
  defaultValues: { username: user.username, position: user.position, stars: user.stars },
})

async function onSubmit(values: ProfileValues) {
  try {
    const updated = await updateUser(user.id, values)
    form.reset(values)
    onSaved(updated)
    toast.success("Perfil atualizado")
  } catch (error) {
    applyServerErrors(form, error, "Não foi possível salvar o perfil")
  }
}

return (
  <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
    <FieldGroup>
      <Controller
        name="username"
        control={form.control}
        render={({ field, fieldState }) => (
          <Field data-invalid={fieldState.invalid}>
            <FieldLabel htmlFor="username">Nome de usuário</FieldLabel>
            <Input {...field} id="username" autoComplete="username" aria-invalid={fieldState.invalid} />
            <FieldError errors={[fieldState.error]} />
          </Field>
        )}
      />
    </FieldGroup>
    {form.formState.errors.root && (
      <Alert variant="destructive"><AlertDescription>{form.formState.errors.root.message}</AlertDescription></Alert>
    )}
    <Button type="submit" disabled={form.formState.isSubmitting}>Salvar</Button>
  </form>
)
```

## Performance

- **Code splitting:** every page is `React.lazy`; keep heavy libraries (recharts, tanstack table) inside the pages that use them. A dialog that pulls a heavy library into a page is lazy-loaded on first open (reference: `PlayerHistoryDialog` in `PeladaDetailPage`).
- **Requests:** never fetch in a loop per item; ask the backend for an endpoint that returns what the screen needs (as `nextDailyDate` and `/users/{id}/peladas` do).
- Don't load every tab up front when a tab is expensive; fetch on first open.
- After a mutation, merge the returned DTO into state instead of refetching the whole detail when the endpoint already returns it.
- `await` only functions that return a promise; a refetch must return its promise if the caller waits for it.
- **Memoize** derived lists that sort/filter on every render (`useMemo`), and stabilize callbacks passed to effects (`useCallback`). Don't sprinkle `React.memo` without a measured problem.
- **Images:** `loading="lazy"` and explicit `width`/`height` on avatars, banners and photos below the fold; keep static assets small (`public/gerrard.png` is 425 KB and used as logo and favicon — use a resized copy).
- **Lists:** paginate long histories (chat, match history) rather than rendering everything.
- **Debounce** search inputs (300 ms, as `AddPlayerDialog` does).
- **Realtime:** there is no polling; session pages update only on reload or after the user's own mutation. If polling is added, put it in the feature hook, pause it when the tab is hidden (`visibilitychange`), stop it when the daily is `FINISHED`/`CANCELED`, and clear the interval on unmount.

## Conventions

- Pages: `src/pages/<Name>Page.tsx` with a **default export** (required by the lazy imports in `App.tsx`). Everything else: **named exports**, PascalCase component files (shadcn primitives keep kebab-case names).
- Formatting: the codebase mixes styles; new and touched files use double quotes, no semicolons, 2-space indent. Add Prettier (`prettier` + `prettier-plugin-tailwindcss`) with that config when convenient and format only files you touch.
- TypeScript strict with `noUnusedLocals`, `noUnusedParameters`, `verbatimModuleSyntax`: use `import type` for type-only imports; no `any`, no `!` non-null assertions to silence errors.
- All user-facing strings in Portuguese, including toasts and `aria-label`s.
- Shared helpers instead of inline repeats: initials (`getInitials`), file URLs (`getFileUrl`, imported where needed — not passed as a prop), days of the week (`DAYS_OF_WEEK`), positions (`getPositionLabel`).
- Stable `key`s from ids; index keys only for skeleton placeholders.
- Tests: pure logic in `src/utils/`, `src/lib/`, `src/schemas/` and `src/api/` gets Vitest tests next to the file (`*.test.ts`).

## Patterns

Follow these when writing new code. Each points to a file that does it right.

### 1. API module → feature hook → page

```ts
// src/api/peladas.ts
export async function getMyPeladas(): Promise<PeladaResponse[]> {
  const response = await apiClient.get<PeladaResponse[]>("/api/v1/peladas/my")
  return response.data
}
```

```ts
// components/pelada/hooks/usePeladaDetail.ts
const { pelada, dailies, ranking, awards, loading, accessDenied, refetch } = usePeladaDetail(id)
```

Reference: `src/api/peladas.ts`, `components/pelada/hooks/usePeladaDetail.ts`, `components/daily/hooks/useDailyDetail.ts`, `components/profile/hooks/useProfile.ts` (results keyed by id, no `setState` in effect bodies).

### 2. Handle every async state

| State | Component |
|-------|-----------|
| Loading | `Skeleton` blocks shaped like the final layout (`pelada/DetailSkeleton`, `daily/DetailSkeleton`, `profile/ProfileSkeleton`) |
| Forbidden (403) | access-denied state in place of the content |
| Load error | `Alert variant="destructive"` or an empty state with a retry |
| Action error | `toast.error(getErrorMessage(error, "…"))` |
| Action success | `toast.success("…")` when it isn't visually obvious |

### 3. Mutations block double submits and report success

```tsx
const [isPending, setIsPending] = useState(false)
async function handleConfirm() {
  setIsPending(true)
  try {
    const updated = await confirmAttendance(daily.id)
    setDaily((prev) => (prev ? { ...prev, ...updated } : prev))
    toast.success("Presença confirmada")
  } catch (error) {
    toast.error(getErrorMessage(error, "Não foi possível confirmar a presença"))
  } finally {
    setIsPending(false)
  }
}
<Button disabled={isPending} onClick={handleConfirm}>Confirmar presença</Button>
```

### 4. Extract hooks from big pages

When a page passes ~250 lines or ~8 `useState`s, move state and handlers into `components/<feature>/hooks/` (reference: `useDailyActions`, `usePeladaActions`, `useResultsForm`).

### 5. Side forms in a Sheet

Reference: `components/CreatePeladaModal.tsx`, `components/EditPeladaModal.tsx`.

### 6. Server-provided permissions

Render admin actions from `daily.isAdmin` (reference: `components/daily/DailyHeader.tsx`); the server stays the authority.

### 7. Pure logic in `utils/` with tests

Reference: `utils/parseSessionMessage.ts`, `utils/matchStats.ts`.

### 8. Theme-aware styling

Tokens and `dark:` only where an asset differs per theme. Reference: shadcn components in `components/ui`, `Button variant="gradient"`.

## Antipatterns

Don't introduce these. Some exist already ("Found in", paths under `src/`); fix them when the change is small, otherwise mention them to the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 1 | Hand-rolled modals (`fixed inset-0 bg-black/50`): no focus trap, Esc or aria | `Dialog` / `AlertDialog` / `Sheet` | — |
| 2 | Raw `<button>`, `<input>`, `<select>`, `<textarea>`, `<label>` | `Button`, `Input`, `Select`, `Textarea`, `Label`/`FieldLabel` | — |
| 3 | Raw palette colors, hex values, `text-white`/`bg-black` on surfaces, screens hardcoded to one theme | Semantic tokens; new tokens in `index.css` for light and dark | `pelada/ComparePlayersDialog.tsx` (always dark), `profile/StatsOverTimeChart.tsx`, `ProfilePage.tsx` KPI icon colors, `HomePage.tsx` hero, `MatchHistoryTable` highlight background, focus rings in the pelada modals, `LandingPage.tsx` (`neutral-*`) |
| 4 | Retyping the brand gradient instead of the variant | `<Button variant="gradient">` | — |
| 5 | Forms with `useState` per field and hand-written validation | react-hook-form + zod + `Field` ([Forms](#forms-mandatory)) | `AuthPage`, `CreatePeladaModal`, `EditPeladaModal`, `FinalizeModal`, `ImportFromMessageModal` |
| 6 | Casting errors inline (`err as { response?: { data?: { message?: string } } }`) or copying an `extractErrorMessage` per file | One `getErrorMessage(error, fallback)` in `lib/errors.ts`; `applyServerErrors` for forms | — |
| 7 | Copy-pasted helpers/constants | One helper in `lib/` or `utils/` | — |
| 8 | Calling `src/api/*` from presentational components or inline `useEffect` fetches in pages | A feature hook that exposes `{ data, loading, error }` | `CreateSessionDialog`, `EditProfileModal`, `FinalizeModal`, `ImportFromMessageModal`, `CreatePeladaModal`, `EditPeladaModal` (submit handlers call `src/api` directly) |
| 9 | Fetching once per item in a loop | A backend endpoint shaped for the screen | — |
| 10 | `await` on a function that returns `void` (toast fires before the reload finishes) | Return the promise from refetch functions | — |
| 11 | Effects with missing dependencies / unstable functions | `useCallback` or move the function inside the effect | — |
| 12 | English user-facing text | Portuguese | — |
| 13 | `window.location.href` for in-app navigation | `useNavigate()` / `<Link>` (the 401 interceptor is the accepted exception today) | `context/AuthContext.tsx:46` |
| 14 | Reading/writing `futspring_token`/`futspring_user` outside AuthContext | `useAuth()` | — |
| 15 | Importing `axios` or calling `fetch` outside `src/api` | Add a function to an `api/*` module | — |
| 16 | Hardcoding the backend URL | `import.meta.env.VITE_API_URL` through `apiClient` / `getFileUrl` | — |
| 17 | Client-only permission checks treated as security | Backend checks; UI only hides | — |
| 18 | Status/position magic strings compared across components | Union types + `Record` maps in `types/` | — |
| 19 | Relative `../` imports in new code | `@/` alias | — |
| 20 | `any`, non-null assertions, loose index signatures | Proper types and narrowing | — |
| 21 | Index keys on real content | Stable ids | — |
| 22 | Placeholder links / fake UI | Real routes, or disabled with "Em breve" | `LandingPage.tsx` GitHub/LinkedIn links |
| 23 | Dead files and components | Delete them | — |
| 24 | Mixing icon libraries | lucide-react only | — |
| 25 | `npm install` / a second lockfile | `bun install`; delete `package-lock.json` | — |
| 26 | Prop-drilling plain imports | Import the helper where it's used | — |
| 27 | Chart configs that disagree with the data colors | One color source (tokens) for config and data | `profile/StatsOverTimeChart.tsx` (hex colors) |
