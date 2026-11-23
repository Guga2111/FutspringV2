# Conventions, patterns and antipatterns

> Code conventions, the patterns to copy and the numbered antipatterns table. Part of the [frontend guide](README.md).

## Conventions

- Pages: `src/pages/<Name>Page.tsx` with a **default export** (required by the lazy imports in `App.tsx`). Everything else: **named exports**, PascalCase component files (shadcn primitives keep kebab-case names).
- Formatting: the codebase mixes styles; new and touched files use double quotes, no semicolons, 2-space indent. Add Prettier (`prettier` + `prettier-plugin-tailwindcss`) with that config when convenient and format only files you touch.
- TypeScript strict with `noUnusedLocals`, `noUnusedParameters`, `verbatimModuleSyntax`: use `import type` for type-only imports; no `any`, no `!` non-null assertions to silence errors.
- All user-facing strings in Portuguese, including toasts and `aria-label`s.
- Shared helpers instead of inline repeats: player avatars (`PlayerAvatar` with the `userId`, never an `Avatar` with initials by hand), positions (`PositionBadge` / `getPositionLabel`, one label map), initials (`getInitials`), authenticated photo URLs (`useImageUrl` from hooks/, imported where needed), days of the week (`DAYS_OF_WEEK`).
- Stable `key`s from ids; index keys only for skeleton placeholders.
- Tests: pure logic in `src/utils/`, `src/lib/`, `src/schemas/` and `src/api/` gets Vitest tests next to the file (`*.test.ts`). Screens are covered by the visual regression below.

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

Reference: `components/pelada/CreatePeladaSheet.tsx`. A pelada's own settings are a page, not a Sheet (`/pelada/:id/settings`).

### 6. Server-provided permissions

Render admin actions from `daily.isAdmin` (reference: `components/daily/DailyHeader.tsx`); the server stays the authority.

### 7. Pure logic in `utils/` with tests

Reference: `utils/parseSessionMessage.ts`, `utils/matchStats.ts`.

### 8. Theme-aware styling

Tokens and `dark:` only where an asset differs per theme. Reference: shadcn components in `components/ui`, `Button variant="brand"`.

## Antipatterns

Don't introduce these. These are the general ones; the rest sit in the document of their subject. Numbers are shared by the whole frontend ([index](conventions.md#antipatterns-by-topic)). "Found in" lists where one still exists: fix it when the change is small, otherwise tell the user.

| # | Don't | Do instead | Found in |
|---|-------|-----------|----------|
| 7 | Copy-pasted helpers/constants | One helper in `lib/` or `utils/` | — |
| 12 | English user-facing text | Portuguese | — |
| 20 | `any`, non-null assertions, loose index signatures | Proper types and narrowing | — |
| 21 | Index keys on real content | Stable ids | — |
| 23 | Dead files and components | Delete them | — |
| 26 | Prop-drilling plain imports | Import the helper where it's used | — |

## Antipatterns by topic

Every antipattern of the frontend, by number. Older records cite these numbers.

| # | Antipattern | Document |
|---|---|---|
| 1 | Hand-rolled modals (`fixed inset-0 bg-black/50`): no focus trap, Esc or aria | [design-system.md](design-system.md#antipatterns) |
| 2 | Raw `<button>`, `<input>`, `<select>`, `<textarea>`, `<label>` | [design-system.md](design-system.md#antipatterns) |
| 3 | Raw palette colors, hex values, `text-white`/`bg-black` on surfaces, screens hardcoded to one theme | [design-system.md](design-system.md#antipatterns) |
| 4 | Retyping the brand color, or `text-white` on a brand fill (3.2:1, fails AA) | [design-system.md](design-system.md#antipatterns) |
| 5 | Forms with `useState` per field and hand-written validation | [forms.md](forms.md#antipatterns) |
| 6 | Casting errors inline (`err as { response?: { data?: { message?: string } } }`) or copying an `extractErrorMessage` per file | [data-layer.md](data-layer.md#antipatterns) |
| 7 | Copy-pasted helpers/constants | [conventions.md](conventions.md#antipatterns) |
| 8 | Calling `src/api/*` from presentational components or inline `useEffect` fetches in pages | [hooks.md](hooks.md#antipatterns) |
| 9 | Fetching once per item in a loop | [performance.md](performance.md#antipatterns) |
| 10 | `await` on a function that returns `void` (toast fires before the reload finishes) | [hooks.md](hooks.md#antipatterns) |
| 11 | Effects with missing dependencies / unstable functions | [hooks.md](hooks.md#antipatterns) |
| 12 | English user-facing text | [conventions.md](conventions.md#antipatterns) |
| 13 | `window.location.href` for in-app navigation | [routing.md](routing.md#antipatterns) |
| 14 | Reading/writing `futspring_user` outside AuthContext, or keeping the access token anywhere but `lib/session.ts` (memory) | [data-layer.md](data-layer.md#antipatterns) |
| 15 | Importing `axios` or calling `fetch` outside `src/api` | [data-layer.md](data-layer.md#antipatterns) |
| 16 | Hardcoding the backend URL | [data-layer.md](data-layer.md#antipatterns) |
| 17 | Client-only permission checks treated as security | [auth.md](auth.md#antipatterns) |
| 18 | Status/position magic strings compared across components | [data-layer.md](data-layer.md#antipatterns) |
| 19 | Relative `../` imports in new code (and `./` inside `components/`) | [structure.md](structure.md#antipatterns) |
| 20 | `any`, non-null assertions, loose index signatures | [conventions.md](conventions.md#antipatterns) |
| 21 | Index keys on real content | [conventions.md](conventions.md#antipatterns) |
| 22 | Placeholder links / fake UI | [routing.md](routing.md#antipatterns) |
| 23 | Dead files and components | [conventions.md](conventions.md#antipatterns) |
| 24 | Mixing icon libraries | [design-system.md](design-system.md#antipatterns) |
| 25 | `npm install` / a second lockfile | [stack.md](stack.md#antipatterns) |
| 26 | Prop-drilling plain imports | [conventions.md](conventions.md#antipatterns) |
| 27 | Chart configs that disagree with the data colors | [design-system.md](design-system.md#antipatterns) |
