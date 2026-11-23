# Frontend guide — Futspring client

React single-page app in `client/`. UI text is in Portuguese (pt-BR); code identifiers are in English.

These documents are the source of truth for how the client is built. Each one covers one subject, with its rules (do this) and the antipatterns it guards against (don't do that). **Before changing code, read every document listed for your task in [What to read for each task](#what-to-read-for-each-task)**. If the code and a document disagree, point it out instead of silently following either one. When a change alters what a document describes, update that document in the same change.

## Documents

### Foundations

| Document | Contents |
|---|---|
| [stack.md](stack.md) | Libraries and their roles, bun commands, CI, build-time environment |
| [structure.md](structure.md) | What lives in each folder and where new code goes (12-file limit per feature folder) |
| [conventions.md](conventions.md) | Code conventions, the patterns to copy and the numbered **antipatterns** table |

### Screens

| Document | Contents |
|---|---|
| [routing.md](routing.md) | Every route of `App.tsx`: what the page shows and who can open it; the app shell; adding a page |
| [navigation.md](navigation.md) | Pelada workspace (layout route, areas, subscription notice, chat) and the mobile shell below 768 px |
| [finance.md](finance.md) | The Financeiro area: `useFinance` state and locks, roles and tabs, creation, selection, WhatsApp sharing, quick payment report, images |

### Data and state

| Document | Contents |
|---|---|
| [data-layer.md](data-layer.md) | Session and API client, the `src/api` modules, DTO types, authenticated photos |
| [hooks.md](hooks.md) | Read, shared-state, mutation and form hook patterns with the hook to copy, the hook index, invariants coupled to the backend |
| [auth.md](auth.md) | What the UI may decide about permissions, invitations, auth return destinations, e-mail verification, unsubscribe links |
| [error-reporting.md](error-reporting.md) | Uncaught errors sent to the API, and what is redacted first |

### UI

| Document | Contents |
|---|---|
| [design-system.md](design-system.md) | Tokens, brand and highlight colors, typography, dark mode, CSP, overlays, **mandatory shadcn/ui rules** |
| [forms.md](forms.md) | **Mandatory** react-hook-form + zod + shadcn `Field` pattern |
| [pwa.md](pwa.md) | Manifest, service worker, updates, offline, Web Push, installation |

### Quality

| Document | Contents |
|---|---|
| [performance.md](performance.md) | Code splitting, requests, memoization, images, lists, realtime |
| [testing.md](testing.md) | Vitest for pure logic, Playwright visual regression |
| [Security review workflow](../../docs/SECURITY_REVIEW.md) | Mandatory review for PRs with security impact |

## What to read for each task

Always read [structure.md](structure.md) and [conventions.md](conventions.md), then:

| Task | Also read |
|---|---|
| Any UI change | [design-system.md](design-system.md), [testing.md](testing.md) (browser check with playwright-cli, visual snapshots), the design skill of the screen ([agent workflow](../../docs/agent-workflow.md#choosing-the-design-skill)) |
| New page or route | [routing.md](routing.md), [navigation.md](navigation.md) |
| Something in a pelada area or on mobile | [navigation.md](navigation.md) |
| Calling the API, new endpoint or DTO | [data-layer.md](data-layer.md), [hooks.md](hooks.md), the backend [API reference](../../core/docs/README.md#api-reference) |
| Form | [forms.md](forms.md) |
| Permissions, login, invitations, session | [auth.md](auth.md), [data-layer.md](data-layer.md#api-client-and-auth) |
| Finance | [finance.md](finance.md) |
| Service worker, install, push | [pwa.md](pwa.md) |
| Heavy library, lists, images | [performance.md](performance.md) |

## Non-negotiable rules

Each rule is detailed in the linked document.

1. **bun only** (`bun.lock` is the only lockfile); **Hugeicons only** for icons ([stack.md](stack.md)).
2. **Data flows api → hook → page.** Only `src/api/*` imports `apiClient`; nothing else imports `axios` or calls `fetch` ([data-layer.md](data-layer.md), [hooks.md](hooks.md)).
3. **The backend is the authority on permissions**; admin checks in the UI only hide controls ([auth.md](auth.md)).
4. **The access token lives only in memory** (`lib/session.ts`); only `AuthContext` touches `futspring_user` ([data-layer.md](data-layer.md#api-client-and-auth)).
5. **shadcn components and semantic tokens only**: no hand-rolled modals or inputs, no raw palette colors or hex; every screen works in light and dark and at 375 px ([design-system.md](design-system.md)).
6. **Every form is react-hook-form + a zod schema + shadcn `Field`**, the schema mirroring the backend DTO and importing `z` from `@/lib/zod` ([forms.md](forms.md)).
7. **All user-facing text in Portuguese**, toasts and `aria-label`s included ([conventions.md](conventions.md#conventions)).
8. **Status-like values are union types with `Record` maps** in `src/types` ([data-layer.md](data-layer.md#types)).
9. **Every page is `React.lazy` with a default export**; heavy libraries stay inside the pages that use them ([routing.md](routing.md), [performance.md](performance.md)).
10. **`@/` imports only**, never `../`; a feature folder holds at most 12 files directly ([structure.md](structure.md)).
11. **Mutations merge the returned DTO** (or refetch, returning the promise) and block double submits ([conventions.md](conventions.md#patterns)).

## PR checklist

- [ ] Files in the right feature folder; no folder over 12 files ([structure.md](structure.md)).
- [ ] Requests through an `api/*` module and a feature hook; loading, 403, error and success states handled ([conventions.md](conventions.md#2-handle-every-async-state)).
- [ ] UI built from shadcn components and semantic tokens ([design-system.md](design-system.md)); checked in the browser with playwright-cli at 375 / 1280 px, light and dark ([testing.md](testing.md#validating-ui-in-the-browser)).
- [ ] Forms with react-hook-form + zod + `Field`; server errors through `applyServerErrors` ([forms.md](forms.md)).
- [ ] Text in pt-BR; icons from Hugeicons; `@/` imports.
- [ ] API contract changed on both sides: `src/api` + `src/types` + zod schema and [data-layer.md](data-layer.md) here, DTO and the backend `api-*.md` ([backend docs](../../core/docs/README.md)).
- [ ] Pure logic in `utils/` / `lib/` / `schemas/` / `api/` with a Vitest test next to it; screen changes with reviewed visual snapshots ([testing.md](testing.md)).
- [ ] Every document this change makes wrong updated in the same change; fixed antipatterns removed from their "Found in" column.
- [ ] Security impact assessed (session, private photos, browser storage, CSP, service worker); [security review workflow](../../docs/SECURITY_REVIEW.md) followed when it applies.
- [ ] `bun run lint && bun run build && bun run test` green in `client/`.
