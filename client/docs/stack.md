# Stack, commands and environment

> Libraries and their roles, the bun commands, CI and the build-time environment. Part of the [frontend guide](README.md).

## Rules

1. **bun only**: never `npm` or `yarn`; `bun.lock` is the only lockfile.
2. **Hugeicons only** for icons; `bunx shadcn add` generates lucide imports, so swap them before committing.
3. **No server-state library**: data is fetched in feature hooks with `useState` / `useEffect` ([hooks.md](hooks.md)).
4. `VITE_API_URL` is read only by `src/lib/config.ts`.
5. Before finishing: `bun run lint && bun run build && bun run test`; lint is blocking.

## Stack

- React 19, TypeScript ~5.9 (strict), Vite 8, Vitest 2
- **bun** as package manager — never use `npm`/`yarn`; `bun.lock` is the only lockfile
- Tailwind CSS 3.4 (`tailwind.config.js` + `postcss.config.js`, `tailwindcss-animate`), class-based dark mode
- shadcn/ui (style `default`, base color `slate`, CSS variables) on top of Radix primitives; components in `src/components/ui`
- Guided tour: `@ark-ui/react` (Ark UI `Tour`, wrapped in `components/ui/tour.tsx`; the only Ark primitive, since Radix/shadcn have no tour)
- Icons: **Hugeicons only** — `@hugeicons/react` (`HugeiconsIcon`) + the free set `@hugeicons/core-free-icons` (`<HugeiconsIcon icon={UserGroupIcon} className="size-4" />`; size with `className`, the default stroke is 1.5, `strokeWidth` where a heavier line is wanted; on standard-density screens (`devicePixelRatio` < 1.5, e.g. a 1080p monitor) `index.css` draws the default 1.5 stroke at 2, because 1.5 renders as a faint ~1px line next to Inter; an explicit `strokeWidth` is left alone). An icon stored as a value (maps such as `AREA_ICONS`, `notificationView().icon`) is typed `IconSvgElement` and rendered with `<HugeiconsIcon icon={Icon} />`. `bunx shadcn add` still generates lucide imports: swap them to Hugeicons before committing
- React Router 7 (declarative `BrowserRouter` + `<Routes>`), Axios, Sonner (toasts)
- Charts: recharts through shadcn `ui/chart.tsx`; tables: `@tanstack/react-table` v8 + shadcn `Table`
- Fonts: `@fontsource-variable/inter`, `@fontsource/barlow-condensed` (see Typography)
- `date-fns`, `react-day-picker` (shadcn `Calendar`), `cmdk` (shadcn `Command`), `vaul` (shadcn `Drawer`)
- Realtime chat: `@stomp/stompjs` + `sockjs-client`
- Drag and drop: `@dnd-kit/core` (the session's teams editor on desktop; pointer and keyboard sensors, pt-BR screen-reader announcements; native HTML5 drag and drop has neither touch nor keyboard)
- QR Codes: `qrcode.react` (`QRCodeSVG`, the app download panel of `/auth` and the desktop view of `/instalar`)
- PWA: `vite-plugin-pwa` (`injectManifest`) + `workbox-precaching` / `workbox-routing` in our own service worker `src/sw.ts` (see [PWA](pwa.md#pwa))
- Forms (see [Forms](forms.md#forms-mandatory)): `react-hook-form` + `zod` 4 + `@hookform/resolvers` + shadcn `Field`
- No server-state library (no react-query): data is fetched in hooks with `useState`/`useEffect`

## Commands

PRs with security impact must follow the mandatory [security review workflow](../../docs/SECURITY_REVIEW.md), using Trail of Bits `differential-review` before opening/marking ready and before merge. Include session handling, private photos, browser storage, CSP and service-worker changes in the impact assessment. Passing frontend CI does not replace that review.

Run from `client/`:

```bash
bun install
bun run dev          # Vite dev server on http://localhost:5173 (proxies /ws to :8080)
bun run build        # tsc -b && vite build
bun run lint         # ESLint 9 (flat config)
bun run test         # vitest run
bun run test:visual:docker   # Playwright visual regression in the official image (needs Docker)
bun run test:visual:update   # same, rewriting the snapshots (review the PNG diff before committing)
bunx shadcn@latest add <component>   # add a shadcn component to src/components/ui
```

CI (`.github/workflows/ci-frontend.yml`) runs `bun install --frozen-lockfile`, lint, build and test on pushes and PRs to `main`/`dev` that touch `client/`; the deploy on `main` waits for it (see [CI/CD](../../core/docs/deploy.md#cicd)). Lint is blocking (0 errors; one `react-hooks/incompatible-library` warning from TanStack Table remains). Before finishing a change run `bun run lint && bun run build && bun run test`.

A second CI job, `visual`, runs the Playwright visual regression in `mcr.microsoft.com/playwright:v<version>-noble` (keep the tag equal to `@playwright/test` in `package.json`); it is not blocking yet and uploads the report when it fails. See [Visual regression](testing.md#visual-regression).

## Environment

- `VITE_API_URL` — backend base URL, read at build time (production: `https://futspring.luisgosampaio.com/api` — nginx strips `/api` before proxying, so requests go to `/api/api/v1/...`; locally it is unset). Falls back to `http://localhost:8080`. The only place that reads it is `src/lib/config.ts` (`API_BASE`), used by `api/client.ts`.
- `RealtimeProvider` (`components/layout/`) opens one `new SockJS("/ws")` connection per logged-in user (relative), shared by the chat and the notifications: it works through the Vite dev proxy (`vite.config.ts`) and in production because the app and the API share an origin behind the reverse proxy.
- `vite.config.ts` defines `global: 'globalThis'` (needed by sockjs-client) and `__APP_VERSION__`, the short git sha of the build (`git rev-parse --short HEAD`, `"dev"` without git; `"test"` in `vitest.config.ts`), sent as `release` with the error reports.
- The backend's CORS allows `ALLOWED_ORIGINS` (default `http://localhost:5173`), so keep the dev server on that port or set `ALLOWED_ORIGINS` for the API.
- The connection needs a valid token (CONNECT without one is rejected); only members can subscribe to a pelada's topic; chat send errors arrive on `/user/queue/errors`, notifications on `/user/queue/notifications`.

## Antipatterns

Don't introduce these. Numbers are shared by the whole frontend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 25 | `npm install` / a second lockfile | `bun install`; delete `package-lock.json` | — |
