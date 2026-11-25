# Folder structure

> What lives in each folder of `client/` and where new code goes. Part of the [frontend guide](README.md).

## Rules

1. A feature's components go in `components/<feature>/`, its hooks in `components/<feature>/hooks/` (one `hooks/` per feature); hooks used by two features go in `src/hooks/`.
2. A feature folder holds **at most 12 files directly**; the 13th splits a group of 3+ related components into a lowercase sub-folder in the same PR (`src/lib/layout.test.ts` enforces it).
3. Pure helpers (no React) go in `src/utils/` or `src/lib/`; form schemas in `src/schemas/<domain>.ts`; DTO types in `src/types/`.
4. Import with `@/`, never `../`.

## Folder structure

| Path | Holds |
|---|---|
| `docs/` | this guide (index in [README.md](README.md)) |
| `e2e/` | Playwright: visual regression (`visual.spec.ts`), flow specs, `mockApi.ts` (the mocked GETs), `fixtures.ts` (typed with `src/types`), `__screenshots__/`, `docker.sh`, its own `tsconfig.json` ([testing.md](testing.md)) |
| `public/` | static files: PWA icons, the favicon and in-app logo (small WebP), the auth and home photos, `landing/` (the owner's pelada photos, with a README) |
| `src/main.tsx` | fonts, `captureInstallPrompt()` and `installErrorReporting()` before the render, then `BrowserRouter` > `AuthProvider` > `App` + toaster + `PwaUpdater` |
| `src/App.tsx` | the routes: lazy pages, one `<Suspense>`, an `<ErrorBoundary>` per route ([routing.md](routing.md)) |
| `src/sw.ts` | the service worker, with its own `tsconfig.sw.json` ([pwa.md](pwa.md)) |
| `src/index.css` | Tailwind layers, design tokens, composite classes, reduced motion, landing motion ([design-system.md](design-system.md)) |
| `src/api/` | one module per backend domain; `client.ts` is the only axios instance ([data-layer.md](data-layer.md)) |
| `src/types/` | API DTO types and the label / style `Record`s of their enums |
| `src/schemas/` | zod schemas mirroring the request DTOs ([forms.md](forms.md)) |
| `src/context/` | `AuthContext` and the context value files of the shell's providers |
| `src/hooks/` | hooks used by more than one feature ([hooks.md](hooks.md)) |
| `src/lib/` | infrastructure helpers (table below) |
| `src/utils/` | pure domain logic, tested next to each file (table below) |
| `src/pages/` | one `<Name>Page.tsx` per route, default export |
| `src/components/ui/` | shadcn primitives: owned code, changed only backward compatible |
| `src/components/*.tsx` | components shared by several features (table below) |
| `src/components/<feature>/` | one folder per feature: `auth`, `account-settings`, `daily`, `finance`, `home`, `install`, `invitations`, `landing`, `layout` (+ `layout/mobile`), `legal`, `notification-settings`, `notifications`, `pelada`, `pelada-settings`, `platform`, `profile`, `subscription`; sub-folders by part (`daily/teams`, `daily/live`, `finance/collection`, `pelada/ranking`…) |

### Shared components (`src/components/`)

Use these instead of rebuilding them:

| Component | For |
|---|---|
| `PlayerAvatar` | a player's photo or initials on their own color from `userId`; `deleted` for an "Ex-jogador" |
| `PeladaAvatar` | a pelada's photo or initials on `getPeladaColor` |
| `PositionBadge`, `StarRow`, `MembershipBadge` | position tag, ★ rating, Mensalista / Diarista tag |
| `SelfConfirmButton`, `BumpAlert` | the caller's confirm button (every state) and the "Você perdeu a vaga" alert |
| `ConfirmActionDialog` | a confirmation `AlertDialog` |
| `SelectionBar` | the sticky bar of a list in selection mode |
| `SettingsCard` | a card of a settings page with its footer (`locked` for a read-only pelada) |
| `ListPagination`, `DateRangeFilter`, `TimeZonePicker` | numbered pages, a period filter, an IANA zone combobox |
| `PrivateRoute`, `ErrorBoundary`, `ScrollToTop`, `ThemedToaster` | routing and app shell pieces |

### `src/lib/`

| Module | For |
|---|---|
| `config.ts` | `API_BASE`, the only reader of `VITE_API_URL` |
| `session.ts` | the in-memory access token, the single-flight refresh, the session interceptors |
| `zod.ts` | `z` with `jitless` (CSP); every schema imports `z` from here |
| `errors.ts`, `form-errors.ts` | `getErrorMessage`, `getErrorStatus`, `getApiErrorBody`; `applyServerErrors` |
| `utils.ts` | `cn()`, `getInitials()`, `normalizeSearch()` (the one accent / case-insensitive search key), `getAvatarColor()` / `getPeladaColor()` |
| `constants.ts` | `DAYS_OF_WEEK`, `dayOfWeekLabel()` |
| `privateImages.ts` | authenticated photo requests shared per account and file (behind `useImageUrl`) |
| `push.ts`, `installPrompt.ts`, `mobileApps.ts` | Web Push in the browser, the captured install prompt, store URLs |
| `errorReporting.ts` | uncaught errors to the API ([error-reporting.md](error-reporting.md)) |
| `legal.ts` | `LEGAL_VERSION` (mirrors the backend's `LegalTerms.CURRENT_VERSION`) |
| `csp.ts`, `contrast.ts` | build-time CSP plugin; contrast math for the token test (never imported by the app) |

### `src/utils/`

| Module | For |
|---|---|
| `dates.ts` | local date parsing, short pt-BR labels, `formatInZone`, `todayInZone`, date ranges |
| `attendance.ts` | confirmed / pending split and order, `getSelfConfirmState` (the confirm button's only source), optimistic `toggledAttendance` / `withCallerAttendance` |
| `sessions.ts`, `sessionGallery.ts`, `home.ts` | next / recent sessions (in the pelada's zone), month groups, gallery filters, Home cards and KPIs |
| `teamDraft.ts`, `liveSession.ts`, `matchStats.ts`, `matchPlayers.ts`, `finishedSession.ts`, `parseSessionMessage.ts` | teams editor draft and pendencies, live session totals and matchups, match stats, finished session summary, WhatsApp text → teams / matches |
| `rank.ts`, `attendanceRanking.ts` | podium styles; attendance ranking positions, filters and labels |
| `peladaAreas.ts`, `mobileNav.ts`, `gestures.ts` | pelada areas and roles, the mobile shell's navigation and "+" menu, gesture decisions ([navigation.md](navigation.md)) |
| `memberFilters.ts`, `bulkMemberActions.ts` | members search and chips; what each bulk action changes and why it is disabled |
| `notifications.ts` | text, link and icon per `NotificationType`, list merging, which types toast |
| `invitations.ts`, `authReturn.ts` | invitation dates; safe local return destinations |
| `finance.ts`, `financeAudit.ts`, `financeWhatsApp.ts`, `financeTour.ts` | charge status and selection rules, audit entry text, the WhatsApp message, tour steps ([finance.md](finance.md)) |
| `subscription.ts`, `platform.ts` | `isWritable`, the subscription notice, landing prices mirroring the backend; billing dates and lines of the admin panel |
| `profile.ts`, `pwaPlatform.ts`, `landingTeamDraw.ts` | profile rates and timeline filters, install context detection, the landing's team draw demo |

## Where new code goes

- A feature's components go in `components/<feature>/`, its hooks in `components/<feature>/hooks/` (one `hooks/` per feature, never split). Hooks used by more than one feature go in `src/hooks/`.
- A feature folder holds at most **12 files directly** (`hooks/` aside); whoever adds the 13th splits it in the same PR: a group of 3 or more components about the same part of the feature goes to a sub-folder named after it, lowercase (`finance/collection`, `daily/teams`, `pelada/ranking`). A component used by two or more sub-folders, or the feature's entry point (`FinanceTab`, `DailyHeader`), stays at the feature root. `components/ui/` (shadcn primitives) and the shared components directly in `components/` are exempt. `src/lib/layout.test.ts` enforces the limit.
- Pure helpers (no React) go in `src/utils/` or `src/lib/`; form schemas in `src/schemas/<domain>.ts`.
- Import with the `@/` alias (`@/` → `src/`, configured in `tsconfig*.json`, `vite.config.ts`, `vitest.config.ts`). There are no `../` imports; don't add any.

## Antipatterns

Don't introduce these. Numbers are shared by the whole frontend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 19 | Relative `../` imports in new code (and `./` inside `components/`) | `@/` alias, also for a sibling file | — |
