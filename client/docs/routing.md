# Routing

> Every route of `App.tsx`, what each page shows and who can open it, plus the app shell. Part of the [frontend guide](README.md).

## Rules

1. Every page is `src/pages/<Name>Page.tsx` with a **default export**, loaded with `React.lazy` in `App.tsx`.
2. Public routes are `<ErrorBoundary>` → page; private routes are children of the one layout route (`<ErrorBoundary>` → `<PrivateRoute>` → `<AppLayout>`), each child wrapped in its own `<ErrorBoundary>`.
3. A private page renders in `PageContainer` with its title in `PageHeader` (`PeladaPageHeader` in a pelada area); it never renders a nav bar.
4. In-app navigation uses `useNavigate()` / `<Link>`.
5. No placeholder links or fake UI: a real route, or a control disabled with "Em breve".

## Routing (`App.tsx`)

| Path | Page | Access | What it is |
|------|------|--------|------------|
| `/` | `LandingPage` | public | the landing page (colour chapters, team draw demo, prices, FAQ); redirects to `/home` when logged in |
| `/auth` (`?tab=login\|register`) | `AuthPage` | public | login and registration in `AuthLayout`, with the app download panel from `lg` |
| `/forgot-password` | `ForgotPasswordPage` | public | asks for a reset link |
| `/reset-password?token=` | `ResetPasswordPage` | public | the reset link from the e-mail |
| `/verification-email` | `VerificationEmailPage` | public | asks for (or resends) the e-mail verification link |
| `/verify-email?token=` | `VerifyEmailPage` | public | verifies the e-mail with the account password |
| `/notifications/unsubscribe?token=` | `NotificationUnsubscribePage` | public | the "Desativar" link of a notification e-mail |
| `/instalar` | `InstallPage` | public | how to install the app, per device (`usePwaInstall` context) |
| `/invitations/:id` | `InvitationPage` | private | one individual invitation: accept / decline |
| `/join/:token` | `JoinPeladaPage` | public | the share link's preview and "Entrar na pelada" |
| `/home` | `HomePage` | private | greeting, bump alerts, upcoming sessions with quick confirm, pending invitations, "Seus números", the caller's peladas, install and push prompts |
| `/peladas` | `PeladasPage` | private | the caller's peladas as a list (the mobile "Peladas" tab) |
| `/notificacoes` | `NotificationsPage` | private | the notification panel as a page (the mobile "Avisos" tab) |
| `/pelada/:id` | `PeladaDetailPage` (layout) → `PeladaOverviewPage` | private | the pelada layout ([navigation.md](navigation.md)); its index is the overview |
| `/pelada/:id/membros` | `PeladaMembersPage` | private | members grid, filters, member actions and bulk selection (admins), invite |
| `/pelada/:id/sessoes` | `PeladaSessionsPage` | private | next session and history as a list or a gallery |
| `/pelada/:id/ranking` | `PeladaRankingPage` | private | ranking, player history, attendance ranking, compare players |
| `/pelada/:id/premios` | `PeladaAwardsPage` | private | awards |
| `/pelada/:id/financeiro` (`/:listId`) | `PeladaFinancePage` | private | the finance area, optionally with one collection open ([finance.md](finance.md)) |
| `/pelada/:id/financeiro/:listId/paguei` | `FinanceQuickReportPage` | private | the WhatsApp quick payment report, outside the pelada layout |
| `/pelada/:id/convites` | `PeladaInvitationsPage` | private (admin) | share link and individual invitations |
| `/pelada/:id/assinatura` | `PeladaSubscriptionPage` | private (creator) | the subscription state, Pix checkout and payments |
| `/pelada/:id/chat` | `PeladaChatPage` | private | the chat as a page (below md) |
| `/pelada/:id/settings` | `PeladaSettingsPage` | private (admin) | the pelada's settings cards, outside the pelada layout |
| `/daily/:id` | `DailyDetailPage` | private | a session: attendance, teams, live results, finished summary |
| `/profile/:id` (`?aba=historico\|peladas`) | `ProfilePage` | private | a player's profile: KPIs, form, performance, history, peladas |
| `/settings` | `AccountSettingsPage` | private | the account: profile, password, appearance, notifications, deletion; "Painel admin" and "Sair" |
| `/settings/notifications` | `<Navigate replace>` | private | old links → `/settings#notificacoes` |
| `/admin` | `AdminOverviewPage` | private (platform admin) | KPIs, "Precisa de você", revenue |
| `/admin/peladas` (`?estado=`) | `AdminPeladasPage` | private (platform admin) | peladas by subscription state, searchable |
| `/admin/peladas/:id` | `AdminPeladaPage` | private (platform admin) | one pelada's subscription: payments, actions, history |
| `/admin/pagamentos` | `AdminPaymentsPage` | private (platform admin) | payments in a period |
| `/admin/usuarios` | `AdminUsersPage` | private (platform admin) | accounts, searchable |
| `/termos`, `/privacidade` | `TermsPage` / `PrivacyPage` | public | legal documents (`components/legal/`) |
| `*` | `NotFoundPage` | public | "Bola fora" |

## App shell and adding a page

- Every page is `React.lazy` with a default export. Public routes: `<ErrorBoundary>` → page. Private routes are children of one **layout route** whose element is `<ErrorBoundary>` → `<PrivateRoute>` → `<AppLayout>`; each child is `<ErrorBoundary>` → page.
- `AppLayout` (`components/layout/`) is the shell: `SidebarProvider` + `AppSidebar` (mounted from md only; logo and the notification bell (stacked under the collapse toggle when collapsed, with a dot instead of the count), "Início" (with the pending invitations count from `MyInvitationsContext` as a brand badge, "20+" while more pages exist; a brand dot when collapsed), "Minhas peladas" with the "+" that opens `CreatePeladaSheet` (each pelada: next session date and its confirmed spots, "Qui, 05/11 · 21:00 · 7/10"; the spots never truncate), `UserMenu` with Perfil / Admin (→ `/admin`, only with `user.isPlatformAdmin` from the stored session: an operator turned admin by SQL logs in again once; the API is the authority) / Configurações (→ `/settings`) / the theme shortcut / Sair) + `SidebarInset` with `MobileShell` (below md the header, the bottom tabs and the gestures; see [Mobile shell](navigation.md#mobile-shell)) around a thin `Alert` "Sem conexão. Algumas informações podem não carregar." while `useOnline()` is false and the page in `<Suspense><Outlet/></Suspense>`, so the shell stays on screen while a page loads. Sidebar: 264 px, collapsible to 64 px (state in `localStorage.sidebar_open`; collapsed, every row (header buttons, Início, "+", peladas, user) is the same 40 px square 12 px from the left, `components/layout/sidebarTile.ts`, checked by the "sidebar collapsed" visual test). Below 768 px there is no sidebar (and no hamburger): the bottom tabs reach every private screen.

- `AppLayout` loads the user's peladas once (`useMyPeladas`) and shares them through `MyPeladasContext` (`useMyPeladasContext()`: `peladas`, `loading`, `error`, `reload`, `updateNextDaily`, `openCreatePelada`), used by the sidebar and the Home page.
- `PrivateRoute` shows a shell-shaped skeleton while the boot refresh restores the session (`restoring`), then lets a session user in or sends to `/auth` (keeping `from`); an expired access token is caught by the 401 interceptor, which refreshes it.
- Pages don't render a nav bar. Their `<main>` is `PageContainer` (one max width, side margins and vertical rhythm for every private page, its skeletons included) and their title goes through `PageHeader` (`PeladaPageHeader` in a pelada area); they use the `.page-enter` fade on their root.
- To add a private page: create `pages/<Name>Page.tsx` (default export), add the lazy import and a child `<Route>` under the layout route in `App.tsx`, and link to it from the sidebar or the page that leads to it.

## Page behavior to keep

What the code of each page must keep doing; most of it is coupled to the backend or to another screen.

**Auth and public links**

- `/auth`: switching `?tab` replaces the history entry and keeps `returnTo`; `?expired=1` shows "Sua sessão expirou". Login returns to the full local `returnTo` or `location.state.from` (path, query and hash), never an external destination (`utils/authReturn.ts`). Registration never logs in: it opens `/verification-email`. The terms checkbox is required and the request sends `termsVersion: LEGAL_VERSION`; an outdated version (409), a wrong password or an e-mail in use land on the form's root `Alert`.
- `/forgot-password` shows the same text whether or not the account exists; the sent state is `formState.isSubmitSuccessful`.
- `/reset-password`: success logs out the local session (the backend revoked every token) and goes to `/auth?tab=login`; without `token`, "Link incompleto".
- `/verify-email` verifies only on an explicit submit with the account password; tokens are never stored in the browser or reported.
- `/notifications/unsubscribe` POSTs the token when it opens (never a GET that changes anything: mail scanners open links), works logged out and with an expired stored session, and only turns e-mails off; re-enabling goes through the logged-in settings.
- `/join/:token` never joins by itself (not after login or registration either), and closing it never declines; a 410 says the link expired or was revoked. The preview shows no private photo.
- `/invitations/:id`: a pending invitation past its expiry reads as expired; a 403 offers "Entrar com outra conta".
- `/instalar` shows steps with the icon of the button to tap, never screenshots (they age with every iOS); the chips at the bottom override the detection.

**App pages**

- `/home`: unanswered sessions come first, then chronological; dismissing the install card stores `localStorage.install_card_dismissed` for 30 days; pull to refresh reloads peladas, invitations and stats.
- `/peladas` reads `MyPeladasContext` only (no request of its own); `/notificacoes` refreshes the list when it opens.
- `/pelada/:id/membros`: the selection survives search and filters; each bulk action counts only the members it changes and is disabled with a hint when nobody would change or the mensalista limit would be passed; Remover / Remover admin skip the creator and the caller. "Convidar jogador" matches a name or a **full** e-mail (the API never matches an e-mail fragment) and creates a pending invitation, never a member.
- `/pelada/:id/sessoes`: the Lista / Galeria choice is in `localStorage.sessions_view`; gallery counts are computed inside the chosen range.
- `/pelada/:id/ranking`: the ⌘K / ⌘I / ⌘P shortcuts only listen on this page; `PlayerHistoryDialog` is lazy-loaded.
- `/pelada/:id/convites` and `/pelada/:id/settings` are admin only and `/pelada/:id/assinatura` creator only: anyone else opening the URL gets an access-denied state with a link back. Without a configured Pix the checkout shows "Pagamento por Pix indisponível…" and no button; "Já fiz o Pix" is disabled for the plan already informed.
- `/pelada/:id/settings`: each card sends only its own fields; Transferir and Excluir are creator only and confirmed by typing the pelada's name; the capacity warning never removes anyone.
- `/daily/:id`: "Iniciar sessão" is disabled while the teams can't start; with short teams or players without a team the confirmation lists them. While the pelada isn't writable every admin action is disabled except Cancelar sessão and removals from the attendance list; the player's own withdrawal stays enabled too.
- `/profile/:id`: the tab is kept in `?aba`; another user's profile only opens when you share a pelada (403 → "Perfil restrito").
- `/settings`: the hash opens its card (`useScrollToHash`); profile and photo changes merge into the session user at once; a password change logs in again with the returned token; the Push switches stay disabled until this device is active; deletion is blocked while `GET /users/me/deletion-blockers` lists anything, and a 409 reloads the list.
- `/admin*`: anyone but a platform admin gets the API's 404 and the page renders `<Navigate to="/404" replace>`; `?estado` is kept in the URL and a new filter goes back to page 1.
- `/termos`, `/privacidade`: changing their text (`components/legal/content.ts`) bumps `LEGAL_VERSION`, mirrored by the backend's `LegalTerms.CURRENT_VERSION`.

## Antipatterns

Don't introduce these. Numbers are shared by the whole frontend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 13 | `window.location.href` for in-app navigation | `useNavigate()` / `<Link>` (the 401 interceptor is the accepted exception today) | `context/AuthContext.tsx:101` |
| 22 | Placeholder links / fake UI | Real routes, or disabled with "Em breve" | — |
