# Pelada settings page

Date: 2026-10-27 · Status: implemented

## Goal

Replace the "Editar pelada" Sheet (`client/src/components/EditPeladaModal.tsx`) with a full settings page for the pelada, modeled on a settings screen with independent cards: each card saves on its own, preferences save instantly, and destructive actions live in a "Zona de perigo" at the end. The page also adds what the Sheet can't do today: change the number of teams and players per team, remove the image and transfer the pelada to another admin.

Shadcn first, responsive (375 px), light and dark.

## Route and entry point

- New private page `PeladaSettingsPage` at `/pelada/:id/settings` (lazy, child of the layout route in `App.tsx`).
- `PeladaBanner`: the pencil ("Editar pelada") and the trash ("Excluir pelada") buttons are replaced by one gear button ("Configurações", `Settings` icon) linking to the page, shown to admins.
- Access: admins only. A member who isn't an admin (or opens the URL directly) sees the access-denied state; a non-member gets the 403 state from `GET /peladas/{id}`. The backend stays the authority on every save.
- `EditPeladaModal`, `useEditPeladaForm` (its logic moves to the per-card hooks) and `DeletePeladaDialog` are removed.

## Layout

- Back link "← {nome da pelada}", title "Configurações", subtitle "Gerencie as informações, o horário e as vagas da pelada. Preferências são salvas automaticamente; os demais blocos têm botão próprio."
- Main column of `Card`s grouped under small uppercase labels (`text-faint-foreground`).
- From `lg`: a sticky "Nesta página" index on the right with one link per card (`scrollIntoView`, no `#hash`), highlighting the last card whose top passed 30% of the viewport (the last card at the bottom of the page; a clicked link stays active during its smooth scroll). Below `lg` the index is hidden and the cards stack full width.
- A card with a form has a footer (`bg-card-elevated`, top border): status text on the left ("Tudo salvo." / "Alterações não salvas"), "Cancelar" (`form.reset()`) and "Salvar alterações" on the right, both disabled while the card isn't dirty or is submitting. Below md the buttons take the full width.
- Leaving the page with a dirty card: no blocker (YAGNI); the status text is enough.

## Sections

| Group | Card | Fields | Saves |
|---|---|---|---|
| PELADA | Informações | photo (`PeladaAvatar` preview, "Trocar imagem", "Remover imagem"), name | image: on pick (validated with `validateImageFile`) and remove on click (confirm with `AlertDialog`); name: button |
| | Horário | day of the week, time, duration (h), time zone (`TimeZonePicker`) | button |
| | Local | address, reference | button |
| SESSÕES | Vagas | number of teams (2–10), players per team (2–20), live "Capacidade: 20 jogadores", mensalista limit ("8 mensalistas hoje · máximo 20") | button |
| | Automação | "Criar a próxima sessão automaticamente" (`Switch`) | instant, with a toast; reverted on error |
| ZONA DE PERIGO | Transferir pelada | creator only | `AlertDialog` (see below) |
| | Excluir pelada | creator only | `AlertDialog` (see below) |

Admins who aren't the creator see the danger-zone cards disabled with "Apenas o criador pode …" (not hidden, so they know who to ask).

Every card with a button is its own react-hook-form + zod form and sends only its own fields to the existing partial `PUT /peladas/{id}` (null fields stay unchanged). A save merges the returned `PeladaResponseDTO` into the page state; errors go through `applyServerErrors` onto that card only.

### Vagas card: capacity changes

- Capacity is `numberOfTeams × playersPerTeam`, computed on read (`Pelada.capacity()`), so a change applies at once to every session, including the ones already scheduled.
- **Nobody is removed.** A scheduled/confirmed session with more confirmed players than the new capacity simply sits over the limit (the attendance list already shows "acima do limite"; new diaristas get "Sessão lotada"). Teams already sorted stay as they are; the admin can sort again (`sort-teams` reads the new numbers).
- Before saving, the card shows a warning `Alert` when the capacity changed and the next session (`nextDaily.confirmedCount` from `MyPeladasContext`) would be over it: "Ela tem 20 confirmados; com 15 vagas, 5 ficam acima do limite. Ninguém é removido." The card description says that sorted teams don't change (the list item has no team count, so no extra request).
- `maxMensalistas` must stay ≤ the new capacity, checked together with the new numbers in the same request (the schema is built from the values being edited, not the saved capacity). Lowering the capacity below the current `maxMensalistas` without lowering the limit → 409 "O limite de mensalistas não pode passar das vagas (16)", shown on the limit field.

### Remove image

"Remover imagem" (only when there is one) → `AlertDialog` → `DELETE /peladas/{id}/image`; the pelada falls back to the gradient with initials everywhere (`PeladaAvatar`).

### Transfer pelada

- Card text: "Passe a pelada para outro admin. Você continua admin, mas deixa de poder excluí-la ou transferi-la."
- "Transferir pelada" opens an `AlertDialog` with a `Select` of the current admins (the creator excluded; with no other admin the button is disabled with "Torne alguém admin antes de transferir"), and an `Input` where the pelada's name must be typed (case-sensitive after trim) to enable "Transferir".
- On success: toast "Pelada transferida para {username}", page state updated; the danger-zone cards become disabled for the caller.

### Delete pelada

- Card text lists what goes away: sessions, results, teams, ranking, awards and chat; the players' global stats are recalculated.
- `AlertDialog` with the name typed to enable "Excluir pelada"; on success navigate to `/home`, toast, and `reload()` from `MyPeladasContext`.

## Backend

| Change | Details |
|---|---|
| `UpdatePeladaRequestDTO` | adds `numberOfTeams` (`@Min(2) @Max(10)`) and `playersPerTeam` (`@Min(2) @Max(20)`), nullable like the rest |
| `PeladaService.updatePelada` | applies the new numbers, then checks `maxMensalistas` (the requested one, or the current) against the resulting capacity under the existing pelada row lock → 409 as today. Closes the "capacity below `maxMensalistas`" Known gap |
| `DELETE /peladas/{id}/image` | admin; clears `pelada.image` and deletes the file with `deleteImageAfterCommit`; returns `PeladaResponseDTO` (idempotent when there is no image) |
| `PUT /peladas/{id}/creator` | `TransferCreatorRequestDTO {userId}` (`@NotNull`); creator only (403 "Apenas o criador pode transferir a pelada"); target must be a member (404) and an admin (409 "Só é possível transferir para um admin"); transferring to yourself → 400. Sets `pelada.creator`; the old creator stays member and admin. Returns `PeladaResponseDTO` |

No migration: no column changes (`creator_id`, `number_of_teams`, `players_per_team` and `image` already exist).

Tests: service tests for the capacity/limit combinations (raise capacity + limit in one request, lower capacity below the limit, lower below the confirmed count of an upcoming session = allowed), transfer (not creator, target not member, target not admin, self, success keeps old creator admin), remove image (non-admin 403, deletes after commit); controller tests for the new routes (200/204, 400, 403 non-member and non-admin, 404).

## Frontend

- `api/peladas.ts`: `UpdatePeladaData` gains `numberOfTeams`, `playersPerTeam`; new `removePeladaImage(id)`, `transferPelada(id, userId)`.
- `schemas/pelada.ts`: one schema per card (`peladaInfoSchema`, `peladaScheduleSchema`, `peladaLocationSchema`, `peladaSlotsSchema` with a `.refine` for `maxMensalistas ≤ teams × players`), each with a comment naming `UpdatePeladaRequestDTO`; `confirmNameSchema(name)` for the danger-zone dialogs. Tests next to the file.
- `components/pelada-settings/` (new feature folder): `SettingsCard` + `SettingsCardFooter` (status text and buttons, reused by every form card), one component per card (each owns its `useForm` + schema), `SettingsNav` (the "Nesta página" index), `DangerZone` with `TransferPeladaDialog` and `DeletePeladaDialog`, `ConfirmNameField`; hooks in `hooks/`: `usePeladaSettings(id)` (loads the pelada, `accessDenied`, `merge`, `nextDaily`) and `useSettingsSubmit(form, save, messages)` (the shared submit: save, reset, toast, server errors).
- Updates `MyPeladasContext` after name/image/transfer/delete so the sidebar stays in sync.
- Docs: FRONTEND.md (route table, folder structure, hooks, the removed Sheet, `EditPeladaModal` as the Sheet-form reference replaced by `CreatePeladaModal`), BACKEND.md (API table, permission matrix with transfer and remove image, Known gaps row removed).

## Out of scope

- Unsaved-changes blocker when leaving the page.
- Leaving the pelada as a member, archiving a pelada.
- Changing sessions already created (date/time of scheduled dailies stay as they were created).
