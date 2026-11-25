# Testing

> Vitest for pure logic, the agent's browser check of every UI change, and the Playwright visual regression. Part of the [frontend guide](README.md).

## Rules

1. Pure logic in `src/utils/`, `src/lib/`, `src/schemas/` and `src/api/` gets a Vitest test next to the file.
2. Screens are covered by the visual regression; a screen that reads a new endpoint gets a fixture in `e2e/`, never a screenshot of its error state.
3. Snapshots are made and checked only in the Playwright image (`bun run test:visual:docker` / `:update`); review the PNG diff before committing.
4. **Every change that alters a screen is looked at in a real browser before it is called done** ([Validating UI in the browser](#validating-ui-in-the-browser)), at 375 px and 1280 px, light and dark.

## Validating UI in the browser

The agent checks its own UI work with **playwright-cli** against the running app (the committed regression suite below is a separate, Docker-only check). Screenshots and saved sessions go to `output/playwright/<task>/`, which git ignores.

1. **Run the app.** `docker compose -f core/docker-compose.dev.yml up -d` (API on 8080, Postgres with the dev seed, Redis), then `bun run dev` in `client/` (5173). The dev seed's users and password are in [core/docs/configuration.md](../../core/docs/configuration.md) (`DataInitializer`; admin `leal@futspring.com`). For a screen the seed can't reach (an empty state, an error), mock the request with `playwright-cli route`.
2. **Log in once.** `playwright-cli open --browser=chromium http://localhost:5173/auth`, fill the form, then `playwright-cli state-save output/playwright/auth.json`; later sessions start with `playwright-cli state-load output/playwright/auth.json`.
3. **Look at every combination the change touches:**

   | Viewport | Command |
   |---|---|
   | phone | `playwright-cli resize 375 812` |
   | desktop | `playwright-cli resize 1280 800` |
   | theme | `playwright-cli set-color-scheme dark` / `light` |

   For each: `playwright-cli snapshot` (structure, names and roles), `playwright-cli screenshot --filename=output/playwright/<task>/<screen>-<width>-<theme>.png`, then read the screenshot.
4. **Check, not just look:**
   - no horizontal scroll: `playwright-cli eval "document.documentElement.scrollWidth > document.documentElement.clientWidth"` must be `false`;
   - no errors: `playwright-cli console error`;
   - the states the change created: loading, empty, error, read-only pelada, admin vs member;
   - keyboard: the new controls are reachable with Tab, have a visible focus ring and a pt-BR accessible name;
   - the rules of [design-system.md](design-system.md): tokens, brand / highlight use, Barlow only for display, shadcn components.
5. **Fix in batches.** Fix everything one round shows, then look again; don't screenshot after every tweak.
6. **Then the regression suite.** If the screen is in `e2e/visual.spec.ts`, update its snapshots with `bun run test:visual:update` (Docker) and review the PNG diff.

Public pages (landing, auth, `/instalar`) also go through the design-taste-frontend inspector (`.taste-skill/`, ignored by git); app screens through impeccable's audit when the change is a redesign or polish. See [docs/agent-workflow.md](../../docs/agent-workflow.md).

## Visual regression

- `e2e/visual.spec.ts` (pages include `/peladas`, `/notificacoes` and the chat page (its shot waits for the chat's last connection state, the tests abort `/ws`), the mobile "+" menu for an admin and a player, the creator's Assinatura area (`/pelada/1/assinatura`, the trial with the annual Pix informed and one payment), the five platform panel pages (`/admin*`, the session user fixture is a platform admin), the record-payment Sheet and a 404 from the panel's API ending on "Bola fora", `/instalar` in four contexts (desktop by default, iPhone, Android and the WhatsApp web view through `test.use({ userAgent })`), the Home install card with an iPhone user agent (mobile projects), `/auth` on both tabs, `/join/:token`, `/invitations/:id`, the 404, the Convites area with its history tab and `/settings`, plus its "Excluir conta" card with pending items, the delete account dialog, the `/settings/notifications` redirect and the "Notificações" card in the push states `inactive`, `active`, `denied` and `needsInstall` (an iPhone user agent without `PushManager`), which also check the Push switches' `aria-label` and disabled state) opens the production build (`vite preview` on port 4318, started by `playwright.config.ts`) with the API mocked and takes full-page screenshots of the main routes and dialogs (compare players, attendance ranking, results, the finance WhatsApp message opened from the collection's deep link), in four projects: `mobile` (375×812) / `desktop` (1280×800) × `light` / `dark`. The theme comes from the emulated OS preference, so the projects also cover the default-theme rule. Each page also asserts no horizontal scroll, and the document's `lang`.
- `e2e/mockApi.ts` answers the GETs the screens read from `e2e/fixtures.ts`; any other request answers 404 and fails the test ("requests without a fixture"), so a screen that starts calling a new endpoint gets a fixture instead of a screenshot of its error state. `/ws` is aborted (no realtime). Login is the cached `futspring_user` set by an init script; the app restores the session through `POST /auth/refresh`, answered by `mockApi` with a JWT-shaped token valid until 2100 and the fixture user. The browser's Web Push is mocked by another init script (`mockPushDevice`): `Notification.permission` and a fake service worker registration whose `pushManager` answer from `window.__pushState` (`{permission, subscribed}`, default not asked and not subscribed), so every page is deterministic; `/push/vapid-public-key` has a fixture. Fixtures are typed with `src/types`, so a DTO change fails `tsc -b` (e2e has its own `tsconfig.json`, referenced by the root one).
- The `/tours` fixture answers both finance tours as seen, so page screenshots never show the tour; the "finance tour on the first visit" test answers none seen and shots the welcome dialog and a tooltip step, then checks Esc posts `seen`.
- Determinism: the clock is frozen at `NOW` (Wednesday 2026-11-04 15:00 in São Paulo; fixture dates are relative to it), `pt-BR` locale (also as Chrome's UI language, `LANG`/`--lang`, which native time inputs follow) and `America/Sao_Paulo` zone, reduced motion, CSS animations disabled, and before each shot the page's ready element (a heading, or `form` on `/auth`), `networkidle`, no `.animate-pulse` Skeleton left, `document.fonts.ready` and the mouse moved away. Per-pixel `threshold` 0.02 (Playwright's default 0.2 lets token changes such as `#1f1f1f` → `#07070D` pass) and an absolute `maxDiffPixels` 50 (a ratio let a recolored badge or number pass on a long page); dialogs allow a 0.04 ratio instead (centered on fractional pixels, their text antialiasing varies slightly).
- Font rendering differs per OS, so snapshots are made and checked only in the Playwright image (`e2e/docker.sh`; `PLATFORM=linux/amd64` emulates CI on Apple silicon — the arm64 and amd64 renders match, except a photo resampled under a rotation: the landing's two rotated prints are painted over in its shot, `mask` in the `pages` table of `visual.spec.ts`). A change that alters a screen on purpose runs `bun run test:visual:update` and commits the new PNGs with the change; review them in the PR diff.
- `serviceWorkers: "block"` in `playwright.config.ts`: the PWA service worker would answer navigations from its precache and fetch outside `page.route`.
- Vitest ignores `e2e/` (`vitest.config.ts`).
