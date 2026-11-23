# Hooks and async state

> How feature hooks load data, run mutations and stay correct under races, with the hook to copy for each case. Part of the [frontend guide](README.md).

## Rules

1. A hook owns the request state and the page renders it (`api → hook → page`). A page or presentational component never calls `src/api/*`.
2. Results are keyed by the id or request they belong to, so another id shows a skeleton, never stale data; no `setState` in effect bodies. Only the latest request writes.
3. A mutation keeps a pending flag that disables its control, then merges the returned DTO (preferred) or refetches; a refetch returns its promise (`useReload` for hooks that reload by bumping a version).
4. Optimistic updates put the previous value back with a toast on error; the server's answer replaces the optimistic value.
5. Close a Radix dialog before removing the item it belongs to.
6. No polling. Shared data is refetched on start, on every (re)connect of the realtime connection (`connectionId`) and on `visibilitychange` → visible.

## Patterns

Copy the reference hook of the case; read it before writing a new one.

### Read hook

Reference: `components/pelada/hooks/usePlayerPeladaHistory.ts` (the reference under the `react-hooks/set-state-in-effect` lint rule).

- Store the result together with the key it was fetched for; derive `loading` as "no result for the current key yet", so switching key shows the skeleton without a `setState` in the effect.
- When only a parameter of the same key changes (a limit, a page), keep the previous rows on screen and expose `fetching` (the screen dims them).
- Return `{ data, loading, error, retry }`; add `accessDenied` (403) or `notFound` (404) when the screen has a state for it.
- Expensive data behind a dialog or tab loads while it is open (`enabled`), not up front (`usePeladaAttendance`).

### Shared app state

Reference: `hooks/useMyPeladas.ts` + `hooks/useMyPeladasContext.ts`.

- Data used by the shell and several pages is loaded **once** by `AppLayout` and shared through a context (`MyPeladasContext`, `MyInvitationsContext`, `NotificationsContext`, `RealtimeProvider`); pages read it with the `use…Context()` hook and never fetch it again.
- A reload keeps what is on screen until the answer arrives; an error goes to `error`, the list stays.
- Realtime goes through the single STOMP connection of `RealtimeProvider` (`useRealtime().subscribe`, kept across reconnects); never open a second connection.

### Mutation hook

Reference: `components/daily/hooks/useDailyActions.ts`, `components/pelada/hooks/usePeladaActions.ts`.

- One function per command, each with its pending flag (or the pending id, which disables that card's menu).
- `try { await api…; merge; toast.success } catch { toast.error(getErrorMessage(error, "…")) }`; no success toast when the change is visible.
- Optimistic attendance goes through `toggledAttendance` / `withCallerAttendance` (`utils/attendance.ts`), merged into the screen and into `MyPeladasContext.updateNextDaily`.
- Bulk commands are not optimistic: they merge the response and return `Promise<boolean>` (true leaves selection mode; false keeps the selection).

### Form hook

Reference: `components/account-settings/hooks/useProfileSettings.ts`; field arrays: `components/daily/hooks/useResultsForm.ts`; two steps: `components/daily/hooks/useImportFromMessage.ts`.

- The hook owns `useForm` + `zodResolver(schema)` and the submit; the component only renders (see [forms.md](forms.md)).
- Server errors go through `applyServerErrors`; a form that saves and stays open calls `form.reset(saved)`.
- A file is kept outside the form and sent after the record exists; its failure only warns (`useCreatePeladaForm`).

## Hook index

| Hook | Where | Owns |
|---|---|---|
| `usePeladaDetail` | `pelada/hooks` | pelada, sessions, ranking and awards of the pelada layout; `merge*` / `drop*` helpers |
| `usePeladaActions` | `pelada/hooks` | member commands (single and bulk) and the caller's attendance in the next session |
| `usePeladaWorkspace` | `pelada/hooks` | what the pelada layout passes to its areas through `<Outlet context>` |
| `usePeladaChat` | `pelada/hooks` | chat history, the pelada topic and the error queue |
| `usePeladaAttendance`, `usePlayerPeladaHistory`, `useComparePlayers` | `pelada/hooks` | the ranking dialogs' data |
| `useUserSearch` | `pelada/hooks` | debounced user search (`tooShort` below 3 characters) |
| `useCreatePeladaForm` | `pelada/hooks` | the "Criar Pelada" Sheet |
| `useSessionsView` | `pelada/hooks` | Lista / Galeria choice in `localStorage.sessions_view` |
| `useDailyDetail`, `useDailyActions`, `useDailyModals` | `daily/hooks` | session detail, its commands and its dialogs |
| `useTeamsEditor`, `useResultsForm`, `useFinalizeForm`, `useImportFromMessage` | `daily/hooks` | the session's forms |
| `useProfile` | `profile/hooks` | profile page (`loading \| ready \| forbidden \| notFound \| error`) |
| `useUserStats`, `useUpcomingAttendance` | `home/hooks` | "Seus números" and the Home cards' confirm / withdraw |
| `usePeladaSettings`, `useSettingsSubmit` | `pelada-settings/hooks` | the settings page's pelada and each card's submit |
| `useProfileSettings`, `useChangePassword`, `useDeletionBlockers`, `useAccountExport`, `useDeleteAccount` | `account-settings/hooks` | the `/settings` cards |
| `useNotificationPreferences`, `useEmailUnsubscribe` | `notification-settings/hooks` | notification preferences and the unsubscribe page |
| `useLoginForm`, `useRegisterForm`, `useForgotPasswordForm`, `useResetPasswordForm` | `auth/hooks` | the auth forms |
| `useInvitation`, `useInvitations`, `useInvitePreview`, `useInvitationResponse`, `useInviteLink` | `invitations/hooks` | invitation screens and the share link |
| `usePeladaSubscription` | `subscription/hooks` | the creator's Assinatura area (idle for anyone else) |
| `usePlatformRequest` (+ `usePageOf`, `useDebouncedValue`), `usePlatformPeladaActions` | `platform/hooks` | the platform admin panel |
| `useFinance` and the other finance hooks | `finance/hooks` | see [finance.md](finance.md) |
| `useMyPeladas`, `useMyInvitations`, `useNotifications` (+ `…Context`) | `src/hooks` | shared app state (called once by `AppLayout`) |
| `RealtimeProvider` / `useRealtime` | `layout/`, `src/hooks` | the user's single STOMP connection |
| `useSelection` | `src/hooks` | selection mode of a list (members, finance charges) |
| `useNowUntil`, `useSelfConfirm` | `src/hooks` | the confirm button's state from `getSelfConfirmState` |
| `usePushSubscription` (+ `syncPushSubscription`) | `src/hooks` | Web Push on this device |
| `useReload`, `useImageUrl`, `useMobileShell`, `useEdgeSwipeBack`, `useVirtualKeyboard`, `usePwaInstall`, `useOnline`, `useTheme`, `useScrollToHash`, `useIsMobile`, `useAuth` | `src/hooks` | shell and platform helpers (see [navigation.md](navigation.md), [pwa.md](pwa.md), [data-layer.md](data-layer.md)) |

## Invariants you can't see from the hook alone

These depend on the backend or on another component; keep them when changing the hook.

- **`useResultsForm` always sends the full list of matches**: the backend treats it as the session's whole set and deletes saved matches left out, so `add` mode sends the saved matches too.
- **`usePeladaActions.removeMember` / `bulkRemove` refetch the sessions** in the background: the backend also takes the member out of upcoming sessions.
- **A mensalista's optimistic confirm is replaced by the server's answer**, which may have bumped a diarista and kept the count.
- **`useTeamsEditor` writes only the teams that changed**, so `useFieldArray` keeps its field ids; a 409 refetches and the draft drops whoever is no longer confirmed (new ones land in "Sem time").
- **`useDailyDetail`:** `DailyDetailPage` keys the session subtree by account and route id; only the latest mounted read updates state or toasts; a load error hides the stale session actions; a 403 clears the detail.
- **`useDailyActions.changeStatus` refetches on a 409** (the teams can't start) and toasts the server's message; `saveTeams` rethrows to the editor form.
- **Notifications:** the counter takes the server's `unreadCount` from each push (a collapsed notification isn't counted twice); `?notificacao={id}` (from a system notification) is marked read and removed from the URL with `replace`; `utils/notifications.ts` is a `Record<NotificationType, …>`, so a new backend type fails the build.
- **Bump alerts are synced both ways:** dismissing calls `markBumpsSeen` and `markBumpRead`; reading a `DIARISTA_BUMPED` notification clears the Home alert through `AppLayout`'s `clearBumps`.
- **`usePushSubscription.enable` asks for the permission inside the click** (iOS only asks inside a gesture), and a failed POST unsubscribes the browser again; `syncPushSubscription` runs once per user when the app opens and resubscribes when the server's VAPID key changed.
- **`useFinanceTour` auto-starts only after the server answered "not seen"**; a failed read never starts it, and a failed "seen" only means it starts again next visit.
- **`useEmailUnsubscribe` POSTs on open** (idempotent, so StrictMode's second run is harmless); mail scanners open links, so it is never a GET that changes anything.
- **`useResetPasswordForm` logs out after success**: the backend ended every session.
- **`usePeladaSettings` / `useSettingsSubmit`:** each settings card sends only its own fields to the partial `PUT /peladas/{id}` and resets to the saved values.
- **`RealtimeProvider`** reconnects through stompjs (1 s, 2 s, 4 s, counted in `onWebSocketClose`), then stays `failed` until the tab becomes visible again or another user logs in; `connectionId` goes up on every CONNECT, which is what shared hooks refetch on.

## Antipatterns

Don't introduce these. Numbers are shared by the whole frontend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 8 | Calling `src/api/*` from presentational components or inline `useEffect` fetches in pages | A feature hook that exposes `{ data, loading, error }` | `CreateSessionDialog` (its submit handler calls `src/api` directly) |
| 10 | `await` on a function that returns `void` (toast fires before the reload finishes) | Return the promise from refetch functions | — |
| 11 | Effects with missing dependencies / unstable functions | `useCallback` or move the function inside the effect | — |
