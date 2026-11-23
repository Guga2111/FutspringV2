# Performance

> Code splitting, requests, memoization, images, lists and realtime. Part of the [frontend guide](README.md).

## Performance

- **Code splitting:** every page is `React.lazy`; keep heavy libraries (recharts, tanstack table) inside the pages that use them. A dialog that pulls a heavy library into a page is lazy-loaded on first open (reference: `PlayerHistoryDialog` in `PeladaDetailPage`).
- **Requests:** never fetch in a loop per item; ask the backend for an endpoint that returns what the screen needs (as `nextDailyDate` and `/users/{id}/peladas` do).
- Don't load every tab up front when a tab is expensive; fetch on first open.
- After a mutation, merge the returned DTO into state instead of refetching the whole detail when the endpoint already returns it.
- `await` only functions that return a promise; a refetch must return its promise if the caller waits for it.
- **Memoize** derived lists that sort/filter on every render (`useMemo`), and stabilize callbacks passed to effects (`useCallback`). Don't sprinkle `React.memo` without a measured problem.
- **Images:** `loading="lazy"` and explicit `width`/`height` on avatars, banners and photos below the fold; keep static assets small: resized WebP, not full-size PNG (the logo is `public/logo-64.webp`, about 3 KB; the favicon `public/gerrard.webp`, about 5 KB).
- **Lists:** paginate long histories (chat, match history) rather than rendering everything.
- **Debounce** search inputs (300 ms, as `InvitePlayerDialog` does).
- **Realtime:** there is no polling. The bell is live (one STOMP connection per user, `RealtimeProvider`); session pages update only on reload or after the user's own mutation. If polling is added, put it in the feature hook, pause it when the tab is hidden (`visibilitychange`), stop it when the daily is `FINISHED`/`CANCELED`, and clear the interval on unmount.

## Antipatterns

Don't introduce these. Numbers are shared by the whole frontend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 9 | Fetching once per item in a loop | A backend endpoint shaped for the screen | — |
