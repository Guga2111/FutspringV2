# Attendance ranking: membership filter, order and search

Date: 2026-10-27 · Status: approved

## Goal

The "Ranking de presença" dialog (`client/src/components/pelada/AttendanceRankingDialog.tsx`, opened from the pelada ⌘K menu, ⌘P) lists every current member's attendance over the last 6 months, best rate first, with no way to filter or reorder it. Add:

- a filter by membership type (mensalista / diarista);
- an ascending order (least present first);
- a search by player name.

Shadcn first, responsive (375 px), light and dark.

## Business rules

| # | Rule |
|---|------|
| R1 | **Membership filter:** Todos (default) / Mensalistas / Diaristas, single choice. Uses the player's **current** status, not their status at each session of the period. |
| R2 | **Position under a filter:** recomputed inside the filtered group. Ties share a position ("5, 5, 5, 8"); the medals (gold/silver/bronze) go to the group's top 3. A player with 0 sessions never gets a medal. |
| R3 | **Order:** clicking the "%" header toggles ↓ most present first (default) and ↑ least present first. It only reverses the list: positions and medals stay with the most present. Ties in ascending order are sorted by name A→Z. |
| R4 | **Name search:** case- and accent-insensitive ("andre" finds "André"), applied after the filter and the ranking. It only hides rows; positions don't change. Local filtering, no debounce. |
| R5 | **Chip counts:** the size of each group ("Mensalistas 8"), independent of the search. |
| R6 | **State:** resets to the default (Todos, ↓, empty search) every time the dialog opens. Nothing is persisted. |
| R7 | **Empty states:** a search with no match shows "Nenhum jogador encontrado para "x"". A chip whose group is empty is disabled and shows 0. With no finalized sessions the current empty state stays and the controls are hidden. |
| R8 | **Row tag:** `MembershipBadge` (Mensalista/Diarista) next to the name, so the type is visible under "Todos". |

Out of scope: sorting by name, changing the period, persisting the filter, position filter.

## API contract (backend + frontend in the same change)

- `PeladaAttendanceDTO.Row` gets `isMensalista: boolean`, filled from the existing "mensalista ids of a pelada" query (3 queries total, still constant).
- The row order sent by the backend is unchanged (rate desc, attended desc, username).
- Tests: `AttendanceService` test (mensalista/diarista flags), controller test asserting the new field.
- Docs: `/peladas/{id}/attendance` row in `core/docs/BACKEND.md`; `client/docs/FRONTEND.md` (dialog description, `usePeladaAttendance`, utils).
- Client: `isMensalista` in `PeladaAttendanceRow` (`client/src/types/pelada.ts`).

## Frontend

### Pure logic (`client/src/utils/attendanceRanking.ts`, with Vitest tests)

- `rankAttendance(rows)` keeps its tie rule and receives already filtered rows.
- New `viewAttendance(rows, { membership, order, query })`: filter by membership → rank → reverse when ascending (ties by name) → apply the search.
- Reuse `MembershipFilter` / `MEMBERSHIP_FILTERS` from `utils/memberFilters.ts` (no duplicated constants); counts per group for the chips.

### UI (`AttendanceRankingDialog.tsx`, installed shadcn components only)

```
Ranking de presença
abr 2026 – out 2026 · 25 sessões finalizadas

[🔍 Buscar jogador…                    ]    ← Input with a Search icon
[Todos 25] [Mensalistas 8] [Diaristas 17]   ← ToggleGroup variant="chip"
┌────┬──────────────────────────┬───────┬────────┐
│ #  │ Jogador                  │ Pres. │  % ↓   │ ← ghost Button inside TableHead
├────┼──────────────────────────┼───────┼────────┤
│ 1º │ (DI) Diegotze [Mensal.]  │ 24/25 │  96%   │
```

- **Sortable header:** `Button variant="ghost"` inside the "%" `TableHead`, lucide `ArrowDown`/`ArrowUp`, `aria-sort` on the `th`, `aria-label` "Ordenar por presença: mais presentes primeiro" / "menos presentes primeiro". No TanStack Table for a single sortable column.
- **Fixed controls:** search and chips stay outside the scroll area; only the table scrolls (sticky header as today).
- **375 px:** full-width search; chips in one row with horizontal scroll (`scrollbar-none`); the row tag is compact (or shown from `sm` if names get too tight); the `Progress` bar stays hidden on mobile.
- Semantic tokens only; all text in pt-BR.

## Verification

- `./mvnw test` in `core/`.
- `bun run lint && bun run build && bun run test` in `client/`.
- Manual check of the dialog at 375 px and desktop, light and dark.
