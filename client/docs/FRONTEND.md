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
- `RealtimeProvider` (`components/layout/`) opens one `new SockJS("/ws")` connection per logged-in user (relative), shared by the chat and the notifications: it works through the Vite dev proxy (`vite.config.ts`) and in production because the app and the API share an origin behind the reverse proxy.
- `vite.config.ts` defines `global: 'globalThis'` (needed by sockjs-client).
- The backend's CORS allows `ALLOWED_ORIGINS` (default `http://localhost:5173`), so keep the dev server on that port or set `ALLOWED_ORIGINS` for the API.
- The connection needs a valid token (CONNECT without one is rejected); only members can subscribe to a pelada's topic; chat send errors arrive on `/user/queue/errors`, notifications on `/user/queue/notifications`.

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
    │   ├── auth.ts, peladas.ts, invitations.ts, dailies.ts, users.ts, chat.ts, notifications.ts
    │   └── *.test.ts     vitest tests for api modules
    ├── components/
    │   ├── ui/           shadcn primitives (owned code, see shadcn rules)
    │   ├── ConfirmActionDialog.tsx   shared AlertDialog for confirmations
    │   ├── MembershipBadge.tsx, SelfConfirmButton.tsx, BumpAlert.tsx   "Mensalista"/"Diarista" tag, the caller's confirm button (window / full / priority states), "Você perdeu a vaga" alert
    │   ├── TimeZonePicker.tsx   searchable IANA zone combobox (Popover + Command)
│   ├── auth/         AuthLayout (shell of the public auth screens: logo, centered card, photo from lg), ResetLinkSent, ResetPasswordForm
│   │   └── hooks/    useForgotPasswordForm, useResetPasswordForm
    │   ├── pelada/       pelada detail feature (PeladaBanner, MembersGrid + MemberCard + MemberSelectionBar, NextSessionCard, SessionHistoryList, RankingTable, AwardsTab, PeladaChat + ChatPanel, dialogs…)
    │   │   └── hooks/    usePeladaDetail, usePeladaActions, useMemberSelection, usePeladaChat, usePlayerPeladaHistory, usePeladaAttendance, useComparePlayers, useUserSearch
    │   ├── daily/        session detail feature (DailyHeader with the admin actions, AttendanceSummaryCard, AttendanceList, TeamsSection + TeamCard/TeamColorDot, LiveSessionCard, LiveLeagueTable, SavedMatchesList, LiveTeamsSection, ResultsDialog + ResultsMatchCard + ScoreStepper, ChampionHero, DailyAwardsGrid, FinishedSessionTabs, DailyStatusBadge, modals…)
    │   │   └── hooks/    useDailyDetail, useDailyActions, useDailyModals, useResultsForm, usePlayerSelection
    │   ├── home/         Home (HomeBanner, UpcomingSessionCard, MyPeladaCard + CreatePeladaCard; hooks useUserStats, useUpcomingAttendance)
    │   ├── profile/      profile feature (KpiCard, charts, MatchHistoryTable, EditProfileModal…) + index.ts barrel
    │   │   └── hooks/    useProfile
    │   ├── layout/       AppLayout (RealtimeProvider + shell with the MyPeladasContext and NotificationsContext providers), RealtimeProvider, AppSidebar, SidebarPeladaItem, UserMenu, MobileTopBar
    │   ├── notifications/  NotificationBell (bell + count; Popover on desktop, Drawer on mobile), NotificationPanel (chips, list, states, "Carregar mais", gear to the e-mail preferences), NotificationItem
    │   ├── notification-settings/  NotificationPreferencesCard (the "E-mail" card: one Switch per category)
    │   │   └── hooks/    useNotificationPreferences, useEmailUnsubscribe
    │   ├── PeladaAvatar.tsx   pelada photo or gradient with initials (sidebar, cards, banner)
    │   ├── PlayerAvatar.tsx, StarRow.tsx   round player avatar (photo / initials, `colorId` for per-player colors) and ★ rating
    │   ├── PrivateRoute.tsx, ErrorBoundary.tsx, ScrollToTop.tsx
    │   ├── pelada-settings/  settings page of a pelada (SettingsCard + SettingsCardFooter, SettingsNav, InfoCard, ScheduleCard, LocationCard, SlotsCard, AutomationCard, DangerZone + TransferPeladaDialog / DeletePeladaDialog, ConfirmNameField)
    │   │   └── hooks/    usePeladaSettings, useSettingsSubmit
    │   └── CreatePeladaModal.tsx   Sheet-based form
    ├── context/          AuthContext.tsx (provider, token storage, axios interceptors), auth-context-value.ts, my-peladas-context-value.ts, notifications-context-value.ts, realtime-context-value.ts
    ├── hooks/            useAuth, useMyPeladas (+ useMyPeladasContext), useNotifications (+ useNotificationsContext), useRealtime, useNowUntil, useSelfConfirm, useTheme (dark class + localStorage.theme), useIsMobile (< 768 px)
    ├── lib/
    │   ├── config.ts     API_BASE (the only reader of VITE_API_URL)
    │   ├── utils.ts      cn(), getFileUrl(), getInitials(), getPeladaGradient()
    │   ├── errors.ts     getErrorMessage(), getErrorStatus(), getApiErrorBody()
    │   ├── form-errors.ts applyServerErrors() for react-hook-form
    │   └── constants.ts  DAYS_OF_WEEK (API value + pt-BR label), dayOfWeekLabel()
    ├── pages/            LandingPage, AuthPage, ForgotPasswordPage, ResetPasswordPage, NotificationUnsubscribePage, HomePage, PeladaDetailPage, PeladaSettingsPage, DailyDetailPage, ProfilePage, NotificationSettingsPage, NotFoundPage
    ├── schemas/          zod schemas mirroring request DTOs: auth.ts, invitation.ts (`InvitePlayerRequestDTO`), daily.ts, pelada.ts, user.ts, upload.ts
    ├── types/            API DTO types: auth, invitation (`Invitation`, `InvitationStatus`, `InviteLink`, `JoinPelada`, restricted `PeladaPreview`), pelada, daily (DailyStatus), stats, user (PublicUser, Position), chat, notification (NotificationType/Category/Award unions, category map and labels, `NotificationPreference` + `notificationPreferenceText` / `NOTIFICATION_CATEGORIES` for the preferences page)
    └── utils/            pure functions: matchStats, matchPlayers, parseSessionMessage (WhatsApp text → teams/matches), dates (local date parsing, short pt-BR labels, formatInZone), liveSession (player totals, scorers line, matchup rotation, goal check), attendance (confirmed/pending split, sort rule and hints, getSelfConfirmState, sortByConfirmation), finishedSession (champion summary, match stat names, player sort, award detail), home (greeting, card dates, upcoming sessions, KPIs), sessions (next session, month groups, pt-BR session labels), memberFilters (position normalization, mensalista/diarista filter, counts, search), bulkMemberActions (`planBulkActions`: targets and disabled hint per bulk action, creator/caller exclusion, mensalista room; remove skip note, name preview, shared stars), notifications (`notificationView`: text parts with bold names, link and icon per `NotificationType`; `notificationPlainText`, `mergeNotifications`, `upsertNotification`, `notificationToast`), attendanceRanking (positions with ties, `viewAttendance`: mensalista/diarista filter ranked inside the group, ascending order, accent-insensitive name search; rate and period labels)
```

Where new code goes:
- A feature's components go in `components/<feature>/`, its hooks in `components/<feature>/hooks/`. Hooks used by more than one feature go in `src/hooks/`.
- Pure helpers (no React) go in `src/utils/` or `src/lib/`; form schemas in `src/schemas/<domain>.ts`.
- Import with the `@/` alias (`@/` → `src/`, configured in `tsconfig*.json`, `vite.config.ts`, `vitest.config.ts`). There are no `../` imports; don't add any.

## Routing (`App.tsx`)

| Path | Page | Access |
|------|------|--------|
| `/` | `LandingPage` (redirects to `/home` when logged in) | public |
| `/auth` (`?tab=login\|register`) | `AuthPage` — returns to the full local `returnTo` destination or `location.state.from` after login/registration (pathname, query and hash preserved; unsafe external destinations rejected); "Esqueceu a senha?" link next to the password | public |
| `/forgot-password` | `ForgotPasswordPage` — e-mail form; on 204 shows `ResetLinkSent` (same text whether or not the account exists) from `formState.isSubmitSuccessful` | public |
| `/reset-password?token=` | `ResetPasswordPage` — the link from the e-mail; new password + confirmation. Success logs out the local session (the backend revoked every token), toasts and goes to `/auth?tab=login`; a bad/expired link shows the API message with "Pedir um novo link". Without `token`: "Link incompleto" card | public |
| `/notifications/unsubscribe?token=` | `NotificationUnsubscribePage` — the "Desativar" link of a notification e-mail, in `AuthLayout`. POSTs the token as soon as it opens (never a GET that changes anything: mail scanners open links), then shows "E-mails de **Presença** desativados" with "Reativar" (resubscribe; then "Desativar de novo") and "Gerenciar preferências" (goes through the login when logged out). An invalid token shows an `Alert` "Link inválido"; without `token`: "Link incompleto". Works logged out and with an expired stored session (the API ignores the header on this route, so no 401 logout) | public |
| `/invitations/:id` | `InvitationPage` — private dedicated preview and explicit Aceitar convite / Recusar, ended/expired/wrong-account/network states and an Abrir pelada shortcut for members | private |
| `/join/:token` | `JoinPeladaPage` — public preview (name/photo/day/time only); login/register preserves the full destination, then requires explicit Entrar na pelada; an existing member gets Abrir pelada; closing never declines | public |
| `/home` | `HomePage` — pending individual invitations with previews and accept/decline actions (`PendingInvitations`, paginated, refreshed on invitation pushes, reconnect and visibility), greeting banner (pending confirmations), a "Você perdeu a vaga" alert per unseen bump (`nextDaily.bump`, "Entendi" → `markBumpsSeen`), next sessions with quick confirm/withdraw (carousel on mobile; the button follows `getSelfConfirmState`: "Abre …" before a diarista's window, "Sessão lotada", or the mensalista hint), "Seus números" (`GET /users/{id}/stats`, wins = match wins) and the user's peladas + create card; peladas come from `MyPeladasContext` | private |
| `/pelada/:id` | `PeladaDetailPage` — admins get a Convites tab (`PeladaInvitations`: active link with copy/replace/revoke confirmations, pending invitations with cancellation and paginated closed history, dates and sender); Convidar jogador (`InvitePlayerDialog`) searches registered accounts by name/e-mail and creates a pending invitation without changing members; banner card (admins get a gear linking to the settings page), pill tabs (members grid with search, position chips and "Mensalistas"/"Diaristas" chips, a Mensalista/Diarista tag per member, admins get "Tornar mensalista"/"Tornar diarista" in the ⋯ menu (disabled with "Limite atingido (8/8)" when full); stats from the ranking already loaded, each member's stars in this pelada; admins get "Definir estrelas" in a member's ⋯ menu, creator and themselves included, which opens `SetPlayerStarsDialog` (closes on save; the result comes as a toast); admins also get "Selecionar" in the filter row: selection mode shows a `Checkbox` on each card (the whole card toggles it, the ⋯ menu is hidden), the selection survives search and filters, and `MemberSelectionBar` (sticky at the bottom of the tab, with 1+ selected) shows "5 selecionados · 2 fora do filtro", Selecionar/Desmarcar visíveis, `Admin ▾` (Tornar / Remover admin), `Tipo ▾` (Tornar mensalista / diarista), `Estrelas` (the stars dialog for every selected member), `Remover` (an `AlertDialog` with up to 5 names) and ✕; below md one `Ações ▾` menu. Each label counts only the members it changes and is disabled with a hint when nobody would change or the mensalista limit would be passed; Remover / Remover admin skip the creator and the caller ("Remover 4 de 6 · o criador e você não entram"). Esc or ✕ exits; sessions: next-session card with confirm/withdraw + history grouped by month; ranking; awards with the leader highlighted), chat from a floating button (Popover panel on desktop, Drawer on mobile), hidden on the admin Convites tab so it cannot cover invitation controls. Ranking rows have a history button and the ⌘K menu has "Histórico do Jogador" (⌘I); both open `PlayerHistoryDialog` (lazy-loaded: summary, goals/assists chart, sessions linking to `/daily/:id`); the ⌘K menu also has "Ranking de Presença" (⌘P), which opens `AttendanceRankingDialog` (last 6 months: played / finalized sessions per member, with ties sharing a position, a Mensalista/Diarista tag per row from `sm`; a name search (case- and accent-insensitive, only hides rows), "Todos"/"Mensalistas"/"Diaristas" chips with each group's size (positions and medals recomputed inside the group, the member's current type) and the "%" header toggling most/least present first (positions stay with the most present); the controls reset on every open and are hidden with no finalized sessions) | private |
| `/pelada/:id/settings` | `PeladaSettingsPage` — admins only (others get an access-denied state with a link back); opened from the gear on the banner. Cards grouped under "Pelada" (Informações: photo saved on pick, "Remover" with an `AlertDialog`, name; Horário: day, time, duration, time zone; Local: address, reference), "Sessões" (Vagas: teams 2–10, players per team 2–20, mensalista limit, live capacity and a warning when the next session would have more confirmed than spots, nobody is removed; Automação: `Switch` saved at once) and "Zona de perigo" (Transferir pelada to another admin and Excluir pelada, both creator only, disabled with a hint for other admins, confirmed by typing the pelada's name in an `AlertDialog`). Each form card has its own footer ("Tudo salvo." / "Alterações não salvas", Cancelar, Salvar alterações) and sends only its fields. From `lg` a sticky "Nesta página" index (`SettingsNav`) scrolls to and highlights the card at the top | private |
| `/daily/:id` | `DailyDetailPage` — header (back link, date, status, metadata, admin actions + ⋯ menu); a "Você perdeu a vaga" alert from `myBump` ("Entendi" → `markBumpsSeen`); before the session: attendance card (confirm / withdraw; the button follows `getSelfConfirmState` with the instants shown in the pelada's `timeZone`), collapsible attendance list (Mensalista/Diarista tags; confirmed in confirmation order with "acima do limite" past the capacity; pending; admin confirm/remove/confirm all; collapsible "Perderam a vaga" from `bumps`) and teams (sort, swap, rename, color); live: live card (finalize / lançar resultados), live league table, saved matches, teams with goals and assists; the results dialog (Dialog on desktop, Drawer on mobile: team chips, score steppers, goals/assists with a goal check that only warns); finished: champion photo + champion team card, awards, pill tabs (final table, match cards, sortable players table, teams with final position) | private |
| `/profile/:id` | `ProfilePage` — stars (the rounded average of the user's stars in their peladas, "Sem avaliação" without peladas; not editable, admins set them per pelada), KPIs, charts, match history, peladas in common, edit profile (own: username, position, photos). Another user's profile is only visible when you share a pelada (403 → message) | private |
| `/settings/notifications` | `NotificationSettingsPage` — opened from the UserMenu "Notificações" item and from the gear in the bell's panel. One "E-mail" card (`NotificationPreferencesCard`) with a `Switch` per category (Presença e vaga, Sessões, Membros, Prêmios, each with its description), saved as soon as it changes (optimistic; back to the previous value with a toast on error, like the Automação card); skeleton while loading, an `Alert` with "Tentar de novo" on error; footer "As notificações dentro do app ficam sempre ativas." | private |
| `*` | `NotFoundPage` | public |

- Every page is `React.lazy` with a default export. Public routes: `<ErrorBoundary>` → page. Private routes are children of one **layout route** whose element is `<ErrorBoundary>` → `<PrivateRoute>` → `<AppLayout>`; each child is `<ErrorBoundary>` → page.
- `AppLayout` (`components/layout/`) is the shell: `SidebarProvider` + `AppSidebar` (logo and the notification bell (stacked under the collapse toggle when collapsed, with a dot instead of the count), "Início", "Minhas peladas" with the "+" that opens `CreatePeladaModal`, `UserMenu` with Perfil / Notificações (→ `/settings/notifications`) / Tema / Sair) + `SidebarInset` with `MobileTopBar` (logo, notification bell, menu) and the page in `<Suspense><Outlet/></Suspense>`, so the shell stays on screen while a page loads. Sidebar: 264 px, collapsible to 64 px (state in `localStorage.sidebar_open`); below 768 px it is a Sheet opened from the top bar that closes when you pick a pelada.
- `AppLayout` loads the user's peladas once (`useMyPeladas`) and shares them through `MyPeladasContext` (`useMyPeladasContext()`: `peladas`, `loading`, `error`, `reload`, `updateNextDaily`, `openCreatePelada`), used by the sidebar and the Home page.
- `PrivateRoute` only checks that a token exists (no expiry check); an expired token is caught by the 401 interceptor.
- Pages don't render a nav bar; they own their padding and use the `.page-enter` fade on their root.
- To add a private page: create `pages/<Name>Page.tsx` (default export), add the lazy import and a child `<Route>` under the layout route in `App.tsx`, and link to it from the sidebar or the page that leads to it.

## Data layer

### API client and auth

- `src/api/client.ts` exports the single axios instance (`apiClient`). **Only `src/api/*` imports it**; never import `axios` or call `fetch` elsewhere.
- `context/AuthContext.tsx` owns the session: token in `localStorage.futspring_token`, user in `localStorage.futspring_user`, `login(token, user)` / `logout()`, read through `useAuth()`. Nothing else touches those keys.
- AuthContext registers the interceptors: the request interceptor (registered when `AuthContext.tsx` loads, so the shell's first requests already carry it) adds `Authorization: Bearer <token>`; the response interceptor (in the provider's effect) logs out on **401** and hard-redirects to `/auth`, except for `/api/v1/auth/*` requests (a wrong password is a 401 the login form shows). There is no refresh token (the JWT lasts 7 days).
- `RealtimeProvider` sends the same token in the STOMP `connectHeaders` (the chat and the notifications share that connection).

### API modules (`src/api/*.ts`)

Each module exports typed async functions that return `response.data`, plus the request payload types (`CreatePeladaData`, `MatchResultInput`, `PopulateDailyInput`…). Response types live in `src/types/` and mirror the backend DTOs in `core/docs/BACKEND.md`.

| File | Endpoints |
|------|-----------|
| `auth.ts` | `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/forgot-password` (`requestPasswordReset`), `/api/v1/auth/reset-password` (`resetPassword`, `ResetPasswordData`) |
| `peladas.ts` | `/api/v1/peladas` (my, detail, create, update, delete, image, players, admin, member stars (`setPlayerStars`), mensalista (`setMensalista`), bulk member actions (`setAdmins`, `setMensalistas`, `setPlayersStars`, `removePlayers`, POST `/players/remove`), remove image (`removePeladaImage`), transfer (`transferPelada`, PUT `/creator`), ranking, attendance (`getPeladaAttendance`), awards, member stats, member history), `/api/v1/users/search` (`PublicUser`, q ≥ 3 chars) |
| `dailies.ts` | `/api/v1/peladas/{id}/dailies`, `/api/v1/dailies/{id}` (detail, confirm, bumps seen (`markBumpsSeen`), admin confirm, confirm all, sort/swap teams, team name/color, status, results, finalize, populate, champion image, delete) |
| `users.ts` | `/api/v1/users/{id}` (profile, update, image, background, stats, timeline, matches, peladas in common) |
| `chat.ts` | `/api/v1/peladas/{id}/messages` (paged history) |
| `notifications.ts` | `/api/v1/notifications` (`getNotifications({page, size, unread})`, pages of `NOTIFICATIONS_PAGE_SIZE` = 20), `unread-count` (`getUnreadNotificationCount`), `{id}/read` (`markNotificationRead`), `read-all` (`markAllNotificationsRead`), `preferences` (`getNotificationPreferences`, `updateNotificationPreferences`: partial, send only the changed category), the public `unsubscribe` / `resubscribe` (`unsubscribeFromEmails(token)`, `resubscribeToEmails(token)`) |

When an endpoint or DTO changes, update the module, `src/types/*` and this table in the same change as the backend.

### Hooks and async state

Data flows **api → hook → page/component**. A hook owns the request state; the page renders it.

- `usePeladaDetail(id)` — loads pelada, dailies, ranking and awards in parallel, each with its own loading flag; exposes `accessDenied` (403), `error`, `refetch*` (each returns its promise) `mergeDaily(item)` to merge a mutation result into the sessions list, `mergeMembers(members)` to merge members returned by a mutation (mensalista, stars, admin; one or a batch) and `dropMembers(userIds)` to take removed members out of the list. Results are keyed by pelada id and responses for another pelada are dropped, so navigating between peladas shows the skeletons, never the previous pelada's data.
- `usePeladaActions(pelada, refetchDailies, mergeDaily, mergeMembers, dropMembers)` — member actions don't hold a dialog open: the dialog closes first and the request runs in the background with a toast, each with a pending member id that disables the card's ⋯. `setMemberMensalista` (merges the returned member; `togglingMensalista`), `setMemberStars` (optimistic: merged at once, the returned member on success, the previous value back on error; `settingStars`), `toggleAdmin` (merges `isAdmin` after the success; `togglingAdmin`), `removeMember` (the card is dimmed while `removingMember`, then `dropMembers` + a background `refetchDailies`, since the backend takes the member out of upcoming sessions), the bulk actions `bulkSetAdmin`, `bulkSetMensalista`, `bulkSetStars` (not optimistic: only the response is merged) and `bulkRemove` (`dropMembers` + background `refetchDailies`), each returning `Promise<boolean>` (true on success, so the grid leaves selection mode; on error a toast and the selection stays) with `bulkPending` (the ids being changed: their cards are dimmed, the selection bar is disabled), and confirm/withdraw the caller in the next session (merged into the sessions list and the sidebar through `updateNextDaily`). The confirm dialog closes before the member leaves the list, so no Radix overlay is unmounted while open (that left `pointer-events: none` on `<body>` and froze the page).
- `useMemberSelection(members, visible)` — selection mode of the members grid: `active`, `selectedIds`, `selected` (derived from the members on screen, so removed members drop out), `outsideFilter`, `allVisibleSelected`, `enter`/`exit`/`toggle`/`toggleVisible`; Esc exits unless an open dialog or menu handled it. The selection survives search and filter changes. What each action changes and why it is disabled comes from `planBulkActions` (`utils/bulkMemberActions.ts`).
- `RealtimeProvider` (`components/layout/`, wraps the shell in `AppLayout`; read it with `useRealtime()`) — the user's single STOMP connection with the token from `useAuth`: `connectionState` (`connected | reconnecting | failed`), `connectionId` (goes up on every CONNECT), `subscribe(destination, handler)` (kept across reconnects, returns the unsubscribe) and `publish`. Reconnects through stompjs (1 s, 2 s, 4 s, counted in `onWebSocketClose`, the only callback a dropped connection fires), then `failed` until the tab becomes visible again or the token changes.
- `usePeladaChat(peladaId)` — chat history over REST + `/topic/pelada/{id}` and `/user/queue/errors` through `useRealtime`, `send`, error queue toasts; `connectionState` is the shared connection's.
- `useMyPeladas()` (in `src/hooks/`, called once by `AppLayout`; read it with `useMyPeladasContext()`) — the sidebar and home list (one request; each item has `isAdmin` and `nextDaily` with the caller's attendance).
- `useUserSearch(query)` — debounced search, keyed by query, `tooShort` below 3 characters.
- `useComparePlayers(peladaId, a, b)` — profile + pelada stats of two players, keyed by the pair.
- `useProfile(userId)` — profile page data keyed by user id; `status` is `loading | ready | forbidden | notFound | error`.
- `useDailyDetail(id)` — loads the daily detail; exposes `setDaily` to merge a mutation's response and `refetch` (returns a promise, doesn't show the skeleton again).
- `useDailyActions({ daily, setDaily, refetch })` — every mutation of the session page with its pending flag.
- `useDailyModals` — open/close state of the daily page's dialogs, plus `attendanceOpen` (null = open until the teams are sorted; the page sets it to false after a successful sort).
- `useResultsForm(daily, mode, onSaved)` — the results dialog's react-hook-form + `useFieldArray` (`makeResultsSchema(teams)` in `schemas/daily.ts`). `mode` `add` (live session: new matches, numbered after the saved ones, with the next matchup suggested by `suggestPairing`) or `edit` (every saved match). It always sends the full list: the backend treats it as the session's full set of matches and deletes saved matches that were left out, so `add` sends the saved matches too. Reloads the detail after saving (league table and stats are recomputed server-side).
- `useNowUntil(iso)` (`src/hooks/`) — the current time, re-read by one `setTimeout` when `iso` is reached (no polling), so the confirm button enables itself when the diarista window opens. `useSelfConfirm(input, timeZone)` turns `getSelfConfirmState` (`utils/attendance.ts`: `confirmed | open | notYet | full | priority`, the single source for the confirm button) into the disabled label ("Abre sáb, 31/10 às 20:00", "Sessão lotada") or the mensalista hint; `SelfConfirmButton` renders it on `NextSessionCard`, `UpcomingSessionCard` and `AttendanceSummaryCard`. The server re-checks everything.
- `usePeladaSettings(id)` (`components/pelada-settings/hooks/`) — the settings page's pelada (`getPelada`, keyed by request; `accessDenied` on 403), `nextDaily` from `MyPeladasContext` (for the capacity warning), `merge(patch)` (applies a saved card and reloads the sidebar's peladas) and `retry`. `detailFields(response)` copies a `PeladaResponse` (PUT, image, transfer) onto the detail.
- `useSettingsSubmit(form, save, messages)` — the submit of a settings card: save, `form.reset(values)` (the footer goes back to "Tudo salvo."), toast; errors through `applyServerErrors`. Each card owns its `useForm` + schema and sends only its own fields to the partial `PUT /peladas/{id}`.
- Bump alerts: `useDailyActions.dismissBump` and `useUpcomingAttendance.dismissBump` call `markBumpsSeen`, clear `myBump` / `nextDaily.bump` and call `markBumpRead(dailyId)` (the server read the bump's notification too). The other way round, reading a `DIARISTA_BUMPED` notification (or "Marcar todas como lidas") clears the Home alert through `AppLayout`'s `clearBumps`.
- `useNotifications(onBumpsRead)` (`src/hooks/`, called once by `AppLayout`; read it with `useNotificationsContext()`) — `unreadCount` (fetched on start, on every (re)connect of the realtime connection (`connectionId`) and on `visibilitychange` → visible; no polling), live pushes from `/user/queue/notifications` (`NotificationPush {notification, unreadCount}`: the counter takes the server's `unreadCount`, so a collapsed notification isn't counted twice; once the list has loaded, `upsertNotification` puts it on top, replacing the entry with the same id; the types in `notificationToast` (`DIARISTA_BUMPED`, `SESSION_CANCELED`, `ATTENDANCE_REMOVED_BY_ADMIN`, `SESSION_REMINDER`) also show a toast with "Ver", which marks it read and navigates), the list (`items`, `filter` `all | unread` sent as `?unread=true`, `loading`, `error`, `hasMore`, `loadingMore`), `refresh` (called when the panel opens: the counter and the first page, keeping the items on screen), `setFilter`, `loadMore` (pages merged by id), `markRead` (optimistic, reverts with a toast), `markAllRead`, `markBumpRead`. Only the latest list request writes. The text, link and icon of each type come from `utils/notifications.ts` (a `Record<NotificationType, …>`, so a new backend type fails the build); clicking an item marks it read, closes the panel and goes to `/daily/:id` (else `/pelada/:id`; `REMOVED_FROM_PELADA` doesn't navigate).
- `usePlayerSelection` — shared multi-select of players (finalize and results modals).
- `useNotificationPreferences()` (`components/notification-settings/hooks/`) — the caller's e-mail preferences (`getNotificationPreferences`, keyed by attempt, `retry`), `emailEnabled` per category with the switches being saved applied on top (optimistic), `saving` per category and `toggle(category, next)` (`PUT` with that category only, merges the four returned, toast; on error the pending value is dropped, so the switch goes back).
- `useEmailUnsubscribe(token)` — the unsubscribe page: POSTs the token on open (keyed by token; idempotent, so StrictMode's second run is harmless), exposes `preference` (category + `emailEnabled`), `loading`, `error` (the API message), `pending`, `resubscribe` and `unsubscribe`.
- `useForgotPasswordForm()` / `useResetPasswordForm(token)` (`components/auth/hooks/`) — react-hook-form + zod (`schemas/auth.ts`) and the submit; the pages only render. The forgot form's success state is `formState.isSubmitSuccessful` (`form.reset()` goes back); the reset calls `logout()` after success because the backend ends every session.
- `usePeladaAttendance(peladaId, enabled)` — loads `getPeladaAttendance` while `enabled` (the dialog is open), refetching on every open; the previous ranking stays on screen during the refetch. Results are keyed by request; returns `{ data, loading, error, retry }`.
- `usePlayerPeladaHistory(peladaId, userId | null, limit | null)` — loads `getPlayerPeladaHistory` (`?limit=`, server-side) for the player history dialog; returns `{ rows, totalSessions, loading, fetching, error, retry }` (rows newest first). `loading` is only true until the first response for that player; changing the limit keeps the previous rows on screen with `fetching` (the dialog dims them). The dialog's period selector (last 5 / 10 / 20 / all, default 5) sets the limit, and the summary tiles, chart and table are derived from the returned rows. Results are keyed by request (no `setState` in the effect body), so switching player never shows the previous player's data. Reference for new fetch hooks under the `react-hooks/set-state-in-effect` lint rule.

Mutations: `try { await api…; toast.success(…) } catch (error) { toast.error(getErrorMessage(error, "…")) }`, keep an `isPending` flag that disables the button, then either merge the returned DTO into state (`setDaily`) or refetch. Prefer merging when the endpoint returns the updated resource.

## Auth & permissions in the UI

- Admin checks in the client only decide what is **shown**. The backend enforces every rule (see "Ownership & authorization" in `BACKEND.md`); never rely on hiding a button for security.
- Pelada admin is derived in `PeladaDetailPage` from `pelada.members[].isAdmin` / `creatorId`. Session admin uses the server-provided `daily.isAdmin`; prefer server-provided flags when they exist.
- A 403 from a detail endpoint renders an "access denied" state (`accessDenied` in `usePeladaDetail`/`useDailyDetail`), not a toast loop.
- Team color can be edited by a team player or an admin (`TeamsSection`: `canEditColor = isOnTeam || daily.isAdmin`), mirroring the backend rule.

## Invitation contracts and authentication destinations

`api/invitations.ts` mirrors the `/api/v1` invitation endpoints documented in `BACKEND.md`: admin POST/GET `/peladas/{id}/invitations` (`pending` filter, `page`, `size=20`), POST cancellation scoped to the pelada, GET/POST/DELETE `/peladas/{id}/invite-link` (GET 204 → null); own GET `/invitations`, GET `/invitations/{id}`, POST accept/decline; public GET `/invite-links/{token}` and authenticated POST `/invite-links/{token}/join`. GETs only read. `invitePlayerSchema` mirrors the positive required `userId`; responses and status labels live in `types/invitation.ts`. Effective expiry comes from the backend; `useNowUntil` also disables an open individual invite at its expiry. The public preview's id is used only for membership/routing; no private detail endpoint is fetched before membership.

Acceptance reloads `MyPeladasContext` and opens the pelada. Link joining lives outside the private shell: navigation mounts the shell and loads the new membership. A link preview never auto-joins, including after login/register. Removed players receive the backend's explanation that an individual invite is required; declined players can still choose to enter by link. `PELADA_INVITATION` notifications carry nullable `invitationId` and link to the dedicated page; `MEMBER_JOINED` is an in-app admin notice. Legacy `ADDED_TO_PELADA` notifications remain readable. Membership e-mail preferences include individual invitations.

`utils/authReturn.ts` preserves a full local destination and rejects external/protocol-relative/backslash redirects. `AuthPage` retains it when switching login/register tabs. A non-auth API 401 logs out and redirects to `/auth?expired=1&returnTo=…`, preserving the full current URL; the expired-session message explains that login is required. Wrong-account invitations offer signing in with another account. Public preview GETs ignore stale backend session headers.

## Types

- `src/types/*` — API response DTOs, matching the backend field names exactly. The backend uses Java records; boolean flags remain `isAdmin`, `isFinished`, `isConfirmed`, and `isMensalista`. PR4 removes only their unused duplicate aliases `admin`, `finished`, `confirmed`, and `mensalista`; client payloads, types, schemas, and behavior are unchanged. Omitted create-pelada settings still default to 2 teams / 5 players per team; partial updates still leave null or omitted fields unchanged.
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
- **Brand green** is a single solid color, the `--brand` token (`#149440`, light + dark; Tailwind `brand`), used by the `.bg-gradient-primary`, `.text-gradient-primary`, `.checkbox-gradient` and `.bg-gradient-date` classes in `index.css` (the names are historical; they no longer draw gradients). Use those classes, `bg-brand`/`text-brand` or the Button `variant="gradient"`; never retype the hex. Only the landing hero (`.landing-hero-bg`) keeps an animated gradient.
- **Chart colors:** `--chart-1` (green) and `--chart-2` (blue), light and dark, exposed as `chart-1`/`chart-2` in Tailwind. `--gold` (`text-gold`) is for stars, trophies and crowns. In a `ChartConfig` use `color: "hsl(var(--chart-1))"` and reference series as `var(--color-<key>)` (reference: `components/pelada/PlayerHistoryDialog.tsx`).
- **New colors** (status badges, positions, more chart series): add tokens to both `:root` and `.dark` (e.g. `--chart-1…5`, `--success`) and use them; don't scatter `bg-green-100 text-green-800` maps without dark variants.
- **Dark mode** is class-based (`.dark` on `<html>`); the choice is saved in `localStorage.theme`, applied in `main.tsx` and toggled from the user menu (`useTheme`). Every new UI must work in both themes.
- **Composition:** `cn()` from `@/lib/utils` for class names; `cva` variants for repeated styles (`components/ui/button-variants.ts` has `default`, `destructive`, `outline`, `secondary`, `ghost`, `link`, `gradient`).
- **Overlays:** `Sheet` for create/edit forms (reference: `CreatePeladaModal`), `Dialog`/`AlertDialog` for confirmations and short forms, `Drawer` for mobile bottom panels (chat), `DropdownMenu` / `Command` for menus.
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

Installed today: alert, alert-dialog, avatar, badge, button, calendar, card, chart, checkbox, collapsible, command, dialog, drawer, dropdown-menu, field, input, label, popover, select, separator, sheet, sidebar, skeleton, switch, table, tabs, textarea, toggle, toggle-group, tooltip, progress (`indicatorClassName`, e.g. `bg-gradient-primary`). Redesign variants: `Tabs` `variant="pill"` (on `TabsList` and `TabsTrigger`; full-width equal tabs below md) in `tabs-variants.ts`, toggle `variant="chip" size="chip"` (filter chips) and `variant="team"` (results dialog team picker) in `toggle-variants.ts`, Badge `variant="status"`, Badge `variant="mensalista"` (`info` / `info-muted`) and `variant="diarista"` (neutral outline), rendered by `MembershipBadge`. `sidebar.tsx` was adapted: 264/64 px widths, no cookie (AppLayout persists the state), `useSidebar`/context in `sidebar-context.ts`, `useIsMobile` from `src/hooks/useIsMobile.ts`; `SheetContent` takes an `overlayClassName`. `field.tsx` was adapted to Tailwind 3 (no container-query orientation). Variants live in `button-variants.ts` / `badge-variants.ts` so the component files only export components.

Not in scope: layout and text elements (`div`, `section`, `main`, `header`, `h1`–`h6`, `p`, `ul`, `img`) and the native `<form>`. Keep those as semantic HTML styled with tokens.

**2. Missing component → add it, don't hand-roll it.** Run `bunx shadcn@latest add <name> --dry-run` first: the registry may want to overwrite existing files (answer "no" unless you mean to update them). This project is on Tailwind 3; if a generated file uses Tailwind 4-only syntax, adapt it before committing.

**3. Customize without forking.** Use `className` (merged with `cn()`) and `variant`/`size`. A style that repeats becomes a new `cva` variant in the `components/ui` file. Don't copy a component's markup into a feature.

**4. `components/ui` is owned code.** Changes affect every screen: keep them backward compatible (add variants, don't change defaults) and check the other usages.

**5. Escape hatch.** A custom interactive component is allowed only when shadcn has no equivalent; build it from Radix/shadcn pieces in `components/<feature>/` with a one-line comment explaining why, and tell the user.

## Forms (mandatory)

Every new form, and every existing form you substantially change, uses **react-hook-form + a zod schema + shadcn `Field`**. Don't validate with `useState`, hand-written `validate()` functions or `FieldErrors` objects. References: `ResetPasswordForm` + `useResetPasswordForm` (`schemas/auth.ts`, client-only confirmation with `.refine`), `CreateSessionDialog` (`schemas/daily.ts`, zod input/output types for a nullable date) and `EditProfileModal` (`schemas/user.ts`, `schemas/upload.ts`). The pelada settings cards (`components/pelada-settings/`, `schemas/pelada.ts`) are the reference for a page of independent forms: one `useForm` per card, a cross-field `superRefine` (`peladaSlotsSchema`: the mensalista limit against the capacity being edited), a file kept outside the form (`InfoCard`) and client-only typed-name confirmations (`makeDeletePeladaSchema`, `makeTransferPeladaSchema`). Still on `useState`: `AuthPage`, `CreatePeladaModal`, `FinalizeModal`, `ImportFromMessageModal`; migrate them as they are touched. `ResultsDialog` (`useResultsForm`) is the reference for a form with a field array.

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
- **Debounce** search inputs (300 ms, as `InvitePlayerDialog` does).
- **Realtime:** there is no polling. The bell is live (one STOMP connection per user, `RealtimeProvider`); session pages update only on reload or after the user's own mutation. If polling is added, put it in the feature hook, pause it when the tab is hidden (`visibilitychange`), stop it when the daily is `FINISHED`/`CANCELED`, and clear the interval on unmount.

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

Reference: `components/CreatePeladaModal.tsx`. A pelada's own settings are a page, not a Sheet (`/pelada/:id/settings`).

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
| 3 | Raw palette colors, hex values, `text-white`/`bg-black` on surfaces, screens hardcoded to one theme | Semantic tokens; new tokens in `index.css` for light and dark | `pelada/ComparePlayersDialog.tsx` (always dark), `profile/StatsOverTimeChart.tsx`, `ProfilePage.tsx` KPI icon colors, `MatchHistoryTable` highlight background, focus rings in the pelada modals, `LandingPage.tsx` (`neutral-*`) |
| 4 | Retyping the brand gradient instead of the variant | `<Button variant="gradient">` | — |
| 5 | Forms with `useState` per field and hand-written validation | react-hook-form + zod + `Field` ([Forms](#forms-mandatory)) | `AuthPage`, `CreatePeladaModal`, `FinalizeModal`, `ImportFromMessageModal` |
| 6 | Casting errors inline (`err as { response?: { data?: { message?: string } } }`) or copying an `extractErrorMessage` per file | One `getErrorMessage(error, fallback)` in `lib/errors.ts`; `applyServerErrors` for forms | — |
| 7 | Copy-pasted helpers/constants | One helper in `lib/` or `utils/` | — |
| 8 | Calling `src/api/*` from presentational components or inline `useEffect` fetches in pages | A feature hook that exposes `{ data, loading, error }` | `CreateSessionDialog`, `EditProfileModal`, `FinalizeModal`, `ImportFromMessageModal`, `CreatePeladaModal` (submit handlers call `src/api` directly) |
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
