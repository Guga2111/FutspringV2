---
name: validar-ui
description: Look at Futspring screens in a real browser with playwright-cli at 375 and 1280 px, light and dark, and check horizontal scroll, console errors, states, keyboard and design-system rules. Use after any change that alters what a screen shows, before saying it is done, or to reproduce a UI bug. Args - the route(s) to check and an optional task name, e.g. "/pelada/1/financeiro finance-receive".
---

# Validar UI

The executable version of [Validating UI in the browser](../../../client/docs/testing.md#validating-ui-in-the-browser). The rules live there and in [design-system.md](../../../client/docs/design-system.md); this skill runs them. It is the agent looking at the running app, not the committed Playwright suite (`client/e2e/`, Docker only).

## Inputs

- **Routes:** from the args, or the routes the current change touches (read `git diff --name-only` and map pages / components to routes with [routing.md](../../../client/docs/routing.md)). A component used on several screens: check the one where it matters most plus one other.
- **Task name:** from the args, or the branch name without its prefix (`feat/finance-receive` → `finance-receive`). Artifacts go to `output/playwright/<task>/` (ignored by git).

## 1. The app must be running

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/          # client
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/auth/refresh   # API: any code but 000
```

If one is down, start it in the background and wait until it answers: API with `docker compose -f core/docker-compose.dev.yml up -d`, client with `bun run dev` in `client/`. Never start a second dev server on top of a running one.

## 2. Session

Public routes need none. For private routes:

```bash
playwright-cli open --browser=chromium http://localhost:5173/auth   # Playwright's Chromium; branded Chrome may not be installed
playwright-cli state-load output/playwright/auth.json   # if the file exists, then goto a private route
```

If the file is missing or the app sends you back to `/auth`, log in with the dev seed (user and password in [core/docs/configuration.md](../../../core/docs/configuration.md), `DataInitializer`; the admin is `leal@futspring.com`): `snapshot`, `fill` e-mail and password, click "Entrar", then `playwright-cli state-save output/playwright/auth.json`. Use another seed user to see the member view. Only the dev seed, never a real account.

## 3. The matrix

For each route, the four combinations:

```bash
playwright-cli goto http://localhost:5173<route>
playwright-cli resize 375 812          # then 1280 800
playwright-cli set-color-scheme light  # then dark
playwright-cli snapshot
playwright-cli screenshot --filename=output/playwright/<task>/<screen>-<width>-<theme>.png
playwright-cli eval "document.documentElement.scrollWidth > document.documentElement.clientWidth"
playwright-cli console error
```

Read every screenshot (the Read tool shows the image). `eval` must print `false`; `console error` must be empty (a dev-only warning is not an error, an uncaught exception or a failed request the screen depends on is). Known noise, present on every page: `Permissions policy violation: unload is not allowed` from `sockjs-client`.

Run the commands one by one or in a loop with separate width and height variables (`resize $W $H`): zsh doesn't split `"375 812"` into two arguments.

## 4. Beyond the happy path

- **States the change created:** loading, empty, error, read-only pelada, admin vs member, long names and big numbers. When the seed can't produce one, mock the API: `playwright-cli route "**/api/v1/<path>" --status=500` or `--body='<json>'`, then `route-list` / unroute when done.
- **Keyboard:** `press Tab` through the new controls; each has a visible focus ring and a pt-BR accessible name in the snapshot. Dialogs and sheets close with Escape and return focus.
- **Design system:** semantic tokens (no raw palette colors), brand for the main action, highlight only for competition and achievements, Barlow Condensed only for display text, shadcn components, pill buttons, touch targets on mobile.

## 5. Fix in batches

Collect everything one round shows, fix it all, then run the matrix again for the affected screens only. Don't screenshot after every tweak.

## 6. Report

End with a short table, one row per route × width × theme: `ok` or what is wrong, plus the screenshot folder. Say plainly what was not checked (a state that could not be reached, a route that needs data the seed lacks).

Then, when the screen is in `client/e2e/visual.spec.ts`, remind that its snapshots must be updated in Docker: `bun run test:visual:update`, and the PNG diff reviewed.

`playwright-cli close` at the end.
