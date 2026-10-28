# Bulk member actions

Date: 2026-10-27 · Status: implemented

## Goal

On the pelada "Membros" tab (`client/src/components/pelada/MembersGrid.tsx`), every admin action lives in each card's ⋯ menu, so changing several members means opening one menu per member. Let an admin select several members and apply one action to all of them at once (e.g. make 5 members admins, remove 4 members).

Shadcn first, responsive (375 px), light and dark.

## Scope

All four member actions get a bulk version, as explicit pairs (never a toggle, since a selection can mix both states):

| Bulk action | Applies to |
|---|---|
| Tornar admin / Remover admin | selected members except the creator; "Remover admin" also skips the caller |
| Tornar mensalista / Tornar diarista | every selected member (creator and caller included) |
| Definir estrelas (one value 1–5 for all) | every selected member (creator and caller included) |
| Remover da pelada | selected members except the creator and the caller |

Out of scope: undo, keyboard shortcuts besides Esc, drag / Shift+click selection, bulk actions on other tabs.

## Business rules

1. **Permission:** caller must be an admin of the pelada (403). Same relationship rules as the single actions.
2. **Creator and caller:** they can be selected (stars, mensalista). Remover and Remover admin skip them on the client, and the bar says so ("Remover 4 de 6 · o criador e você não entram"). Leaving the pelada or demoting yourself is still done from your own card. The server rejects the creator in remove/demote with 403, as today.
3. **Atomic:** one request and one transaction per batch. Any invalid target (not a member → 404, creator → 403, limit → 409) fails the whole batch and nothing changes.
4. **Idempotent per member:** making an admin who already is one is not an error.
5. **Mensalista limit:** "Tornar mensalista" is blocked when `current mensalistas + newly marked > maxMensalistas`. The client disables the action ("Cabem 2, você selecionou 5"), and the server locks the pelada row and answers 409 "Limite de mensalistas atingido: cabem 2, foram selecionados 5". The system never picks who gets the status.
6. **Remove:** same effects as the single removal (stars and mensalista rows deleted, taken out of attendance and teams of `SCHEDULED`/`CONFIRMED` sessions; live, finished and canceled sessions, results, rankings and stats untouched), done with batch queries, not one per member.

## API

| Method | Path | Body | Response |
|---|---|---|---|
| PUT | `/api/v1/peladas/{id}/players/admin` | `{userIds, isAdmin}` | `List<PeladaMemberDTO>` |
| PUT | `/api/v1/peladas/{id}/players/mensalista` | `{userIds, isMensalista}` | `List<PeladaMemberDTO>` |
| PUT | `/api/v1/peladas/{id}/players/stars` | `{userIds, stars 1–5}` | `List<PeladaMemberDTO>` |
| POST | `/api/v1/peladas/{id}/players/remove` | `{userIds}` | 204 |

- `userIds`: `@NotEmpty`, `@Size(max = 100)`, no duplicates (400).
- Every target must be a member (404 for the batch, naming the player when possible).
- `POST .../remove` instead of `DELETE` with a body (poorly supported by proxies and axios).
- The single endpoints stay and delegate to the same service logic with a one-element list; no duplicated rules.
- Tests: controller (happy path, 400 empty/duplicate/oversized list, 403 non-member and non-admin, 404 non-member target, creator 403, 409 limit), service tests for atomicity (one invalid target changes nothing) and the remove side effects.
- No schema change, so no migration.

## UX

**Entering selection:** an admin-only "Selecionar" button in the filter row turns on selection mode. Each card shows a `Checkbox`, the whole card becomes clickable, and the ⋯ menu is hidden. "✕" or Esc exits and clears the selection.

**Selection and filters:** the selection persists across search and filter changes. "Selecionar visíveis" marks the visible members and turns into "Desmarcar visíveis" when all of them are marked. Actions apply to every selected member, and the bar shows "5 selecionados · 2 fora do filtro".

**Action bar** (shown with 1+ selected, fixed at the bottom of the content area, `bg-card shadow-panel`):
- Desktop: the count, Selecionar/Desmarcar visíveis, `Admin ▾` (Tornar / Remover admin), `Tipo ▾` (Tornar mensalista / diarista), `Estrelas` (dialog with a 1–5 picker, "Definir estrelas de 5 membros"), `Remover` (destructive) and ✕.
- 375 px: the count, an `Ações ▾` `DropdownMenu` with the seven actions, and ✕.

**Action states:**
- Each label counts only the members it changes ("Tornar admin (3)").
- An action is disabled with a hint when nobody would change ("Todos já são admins") or when the mensalista limit would be passed.

**Confirmation:** only Remover opens an `AlertDialog` ("Remover 4 membros?", up to 5 names then "+3", "Eles saem das sessões que ainda não começaram"). The other actions run directly.

**Request lifecycle** (same non-blocking pattern as `usePeladaActions`):
- Any dialog closes before the request.
- Affected cards are dimmed (`aria-busy`) and the bar is disabled while it runs.
- Success: merge the returned members (new `mergeMembers(list)` in `usePeladaDetail`); removed members leave with `dropMembers(ids)` plus a background `refetchDailies`. Then a toast ("3 membros agora são admins"), and the selection is cleared and the mode exits.
- Error: `toast.error(getErrorMessage(...))`, the selection stays, and nothing changes.
- Bulk stars are not optimistic (unlike the single action): only the response is merged.

## Docs to update with the implementation

`core/docs/BACKEND.md` (API reference, permission matrix), `client/docs/FRONTEND.md` (`/pelada/:id` route row, `api/peladas.ts` row, `usePeladaDetail` / `usePeladaActions` hooks), `src/api/peladas.ts` + `src/types/pelada.ts` + zod schema for the stars value.
