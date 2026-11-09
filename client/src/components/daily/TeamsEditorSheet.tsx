import { useState, type ReactNode } from "react"
import { Controller } from "react-hook-form"
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type Announcements,
  type DragEndEvent,
  type DragStartEvent,
  type UniqueIdentifier,
} from "@dnd-kit/core"
import { HugeiconsIcon } from "@hugeicons/react"
import { Add01Icon, Delete02Icon, DragDropVerticalIcon } from "@hugeicons/core-free-icons"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Drawer, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle } from "@/components/ui/drawer"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { StarRow } from "@/components/StarRow"
import { TeamColorDot } from "@/components/daily/TeamColorDot"
import { useTeamsEditor } from "@/components/daily/hooks/useTeamsEditor"
import { useIsMobile } from "@/hooks/useIsMobile"
import { cn } from "@/lib/utils"
import { pendencyLines } from "@/utils/teamDraft"
import type { SaveTeamsValues } from "@/schemas/daily"
import type { DailyDetail, PlayerDTO } from "@/types/daily"

interface TeamsEditorSheetProps {
  daily: DailyDetail
  onSave: (values: SaveTeamsValues) => Promise<void>
  refetch: () => Promise<void>
  onClose: () => void
}

type Editor = ReturnType<typeof useTeamsEditor>

const UNASSIGNED = "unassigned"
const playerCount = (n: number) => (n === 1 ? "1 jogador" : `${n} jogadores`)

// "Montar times": the admin builds every team by hand. Desktop drags players between columns (@dnd-kit, pointer
// and keyboard); mobile taps a player and picks the team in a Drawer. Mounted only while open.
export function TeamsEditorSheet({ daily, onSave, refetch, onClose }: TeamsEditorSheetProps) {
  const isMobile = useIsMobile()
  const editor = useTeamsEditor({ daily, onSave, refetch, onSaved: onClose })
  const { form, pendencies } = editor
  const submitting = form.formState.isSubmitting
  const lines = pendencyLines(pendencies, daily.playersPerTeam)

  return (
    <Sheet open onOpenChange={(open) => !open && !submitting && onClose()}>
      <SheetContent
        side="right"
        className={cn(
          "flex w-full flex-col gap-0 p-0",
          isMobile ? "max-w-none sm:max-w-none" : "sm:max-w-[min(1120px,96vw)]",
        )}
      >
        <SheetHeader className="border-b px-5 py-4 text-left">
          <SheetTitle>Montar times</SheetTitle>
          <SheetDescription>
            {isMobile
              ? "Toque num jogador para escolher o time. Só confirmados entram nos times."
              : "Arraste os jogadores entre as colunas. Só confirmados entram nos times."}
          </SheetDescription>
        </SheetHeader>

        <form id="teams-editor" noValidate onSubmit={editor.onSubmit} className="min-h-0 flex-1 overflow-auto px-5 py-4">
          {isMobile ? <MobileBoard editor={editor} daily={daily} /> : <DesktopBoard editor={editor} daily={daily} />}
        </form>

        <SheetFooter className="flex-col gap-3 border-t px-5 py-4 sm:flex-col sm:space-x-0">
          {lines.length > 0 && (
            <ul aria-live="polite" className="flex flex-col gap-0.5 text-body-sm text-muted-foreground">
              {lines.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          )}
          {form.formState.errors.root && (
            <Alert variant="destructive">
              <AlertDescription>{form.formState.errors.root.message}</AlertDescription>
            </Alert>
          )}
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="outline" disabled={submitting} onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" form="teams-editor" variant="brand" disabled={submitting || pendencies.blocking}>
              {submitting ? "Salvando…" : "Salvar times"}
            </Button>
          </div>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

function columnStars(ids: number[], playersById: Map<number, PlayerDTO>) {
  return ids.reduce((sum, id) => sum + (playersById.get(id)?.stars ?? 0), 0)
}

// Header of a team column: color, name input, count, stars and remove
function TeamHeader({ editor, index, playersPerTeam }: { editor: Editor; index: number; playersPerTeam: number }) {
  const team = editor.teams[index]
  const color = editor.fields[index]?.color ?? null
  const count = team?.playerIds.length ?? 0
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center gap-2">
        <TeamColorDot color={color} className="size-3" />
        <Controller
          name={`teams.${index}.name`}
          control={editor.form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid} className="min-w-0 flex-1 gap-1">
              <FieldLabel htmlFor={`team-name-${index}`} className="sr-only">
                Nome do time {index + 1}
              </FieldLabel>
              <Input
                {...field}
                id={`team-name-${index}`}
                maxLength={30}
                aria-invalid={fieldState.invalid}
                className="h-8 font-semibold"
              />
              <FieldError errors={[fieldState.error]} />
            </Field>
          )}
        />
        {editor.canRemoveTeam && (
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={`Remover ${team?.name || `time ${index + 1}`}`}
            onClick={() => editor.removeTeamAt(index)}
            className="size-8 shrink-0 text-muted-foreground"
          >
            <HugeiconsIcon icon={Delete02Icon} className="size-4" />
          </Button>
        )}
      </div>
      <div className="flex items-center justify-between text-caption text-muted-foreground">
        <span className={cn(count < playersPerTeam && "font-semibold text-warning")}>
          {count}/{playersPerTeam}
        </span>
        <span className="text-gold">{columnStars(team?.playerIds ?? [], editor.playersById)} ★</span>
      </div>
    </div>
  )
}

function PlayerLine({ player }: { player: PlayerDTO }) {
  return (
    <>
      <PlayerAvatar userId={player.id} username={player.username} image={player.image} deleted={player.isDeleted} />
      <span className="min-w-0 flex-1 truncate text-left text-sm font-medium">{player.username}</span>
      <StarRow stars={player.stars} />
    </>
  )
}

function emptyTeamError(editor: Editor, index: number) {
  return editor.form.formState.errors.teams?.[index]?.playerIds?.message
}

// --- Desktop: drag and drop between columns ---

function DesktopBoard({ editor, daily }: { editor: Editor; daily: DailyDetail }) {
  const [dragging, setDragging] = useState<PlayerDTO | null>(null)
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor),
  )

  const playerName = (id: UniqueIdentifier) => editor.playersById.get(Number(String(id).replace("player-", "")))?.username ?? "Jogador"
  const columnLabel = (index: number | null | undefined) =>
    index == null ? "Sem time" : editor.teams[index]?.name || `Time ${index + 1}`

  const announcements: Announcements = {
    onDragStart: ({ active }) => `Pegou ${playerName(active.id)}.`,
    onDragOver: ({ active, over }) =>
      over ? `${playerName(active.id)} sobre ${columnLabel(over.data.current?.index)}.` : `${playerName(active.id)} fora das colunas.`,
    onDragEnd: ({ active, over }) =>
      over ? `${playerName(active.id)} movido para ${columnLabel(over.data.current?.index)}.` : `${playerName(active.id)} solto sem mudar de time.`,
    onDragCancel: ({ active }) => `Movimento de ${playerName(active.id)} cancelado.`,
  }

  function handleDragStart({ active }: DragStartEvent) {
    setDragging(editor.playersById.get(active.data.current?.playerId as number) ?? null)
  }

  function handleDragEnd({ active, over }: DragEndEvent) {
    setDragging(null)
    if (!over) return
    const from = active.data.current?.index as number | null
    const to = over.data.current?.index as number | null
    if (from !== to) editor.move(active.data.current?.playerId as number, to)
  }

  return (
    <DndContext
      sensors={sensors}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={() => setDragging(null)}
      accessibility={{
        announcements,
        screenReaderInstructions: {
          draggable:
            "Para mover um jogador, pressione espaço ou Enter, use as setas para chegar ao time e pressione espaço ou Enter de novo para soltar. Esc cancela.",
        },
      }}
    >
      <div className="flex min-h-full gap-3 overflow-x-auto pb-2">
        <DropColumn id={UNASSIGNED} index={null} header={<UnassignedHeader editor={editor} />}>
          {editor.unassigned.map((player) => (
            <DraggablePlayer key={player.id} player={player} index={null} />
          ))}
          {editor.unassigned.length === 0 && <EmptyHint>Todos os confirmados estão em times</EmptyHint>}
        </DropColumn>
        {editor.fields.map((field, index) => (
          <DropColumn
            key={field.id}
            id={`team-${field.id}`}
            index={index}
            header={<TeamHeader editor={editor} index={index} playersPerTeam={daily.playersPerTeam} />}
            error={emptyTeamError(editor, index)}
          >
            {(editor.teams[index]?.playerIds ?? []).map((id) => {
              const player = editor.playersById.get(id)
              return player ? <DraggablePlayer key={id} player={player} index={index} /> : null
            })}
            {(editor.teams[index]?.playerIds.length ?? 0) === 0 && <EmptyHint>Arraste jogadores para cá</EmptyHint>}
          </DropColumn>
        ))}
        {editor.canAddTeam && (
          <Button type="button" variant="outline" onClick={editor.appendTeam} className="h-10 shrink-0 gap-1.5 self-start">
            <HugeiconsIcon icon={Add01Icon} className="size-4" />
            Adicionar time
          </Button>
        )}
      </div>
      <DragOverlay>
        {dragging && (
          <div className="flex w-[240px] items-center gap-2.5 rounded-lg border bg-card px-2.5 py-2 shadow-menu">
            <PlayerLine player={dragging} />
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
}

function UnassignedHeader({ editor }: { editor: Editor }) {
  return (
    <div className="flex flex-col gap-1.5">
      <h3 className="flex h-8 items-center text-sm font-semibold">Sem time</h3>
      <div className="flex items-center justify-between text-caption text-muted-foreground">
        <span>{playerCount(editor.unassigned.length)}</span>
        <span className="text-gold">{editor.unassigned.reduce((sum, p) => sum + p.stars, 0)} ★</span>
      </div>
    </div>
  )
}

function DropColumn({
  id,
  index,
  header,
  error,
  children,
}: {
  id: string
  index: number | null
  header: ReactNode
  error?: string
  children: ReactNode
}) {
  const { setNodeRef, isOver } = useDroppable({ id, data: { index } })
  return (
    <section
      ref={setNodeRef}
      className={cn(
        "flex w-[260px] shrink-0 flex-col gap-2 rounded-xl border bg-card p-3 transition-colors",
        index === null && "bg-card-elevated",
        isOver && "border-brand",
        error && "border-destructive",
      )}
    >
      {header}
      <ul className="flex min-h-24 flex-col gap-1">{children}</ul>
      {error && <p className="text-caption text-destructive">{error}</p>}
    </section>
  )
}

function DraggablePlayer({ player, index }: { player: PlayerDTO; index: number | null }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `player-${player.id}`,
    data: { playerId: player.id, index },
  })
  return (
    <li
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      aria-label={`Mover ${player.username}`}
      className={cn(
        "flex cursor-grab touch-none items-center gap-2.5 rounded-lg border border-transparent px-2 py-1.5 hover:bg-row-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        isDragging && "opacity-40",
      )}
    >
      <HugeiconsIcon icon={DragDropVerticalIcon} className="size-3.5 shrink-0 text-faint-foreground" />
      <PlayerLine player={player} />
    </li>
  )
}

function EmptyHint({ children }: { children: ReactNode }) {
  return (
    <li className="flex flex-1 items-center justify-center rounded-lg border border-dashed border-input px-3 py-5 text-center text-caption text-muted-foreground">
      {children}
    </li>
  )
}

// --- Mobile: tap a player, pick the team in a Drawer ---

function MobileBoard({ editor, daily }: { editor: Editor; daily: DailyDetail }) {
  const [moving, setMoving] = useState<{ player: PlayerDTO; index: number | null } | null>(null)

  function pick(target: number | null) {
    if (moving && moving.index !== target) editor.move(moving.player.id, target)
    setMoving(null)
  }

  const playerButton = (player: PlayerDTO, index: number | null) => (
    <li key={player.id}>
      <Button
        type="button"
        variant="ghost"
        onClick={() => setMoving({ player, index })}
        aria-label={`Mover ${player.username}`}
        className="h-auto w-full justify-start gap-2.5 rounded-lg px-2 py-1.5 font-normal"
      >
        <PlayerLine player={player} />
      </Button>
    </li>
  )

  return (
    <div className="flex flex-col gap-3">
      <section className="flex flex-col gap-2 rounded-xl border bg-card-elevated p-3">
        <UnassignedHeader editor={editor} />
        <ul className="flex flex-col gap-1">
          {editor.unassigned.map((player) => playerButton(player, null))}
          {editor.unassigned.length === 0 && <EmptyHint>Todos os confirmados estão em times</EmptyHint>}
        </ul>
      </section>
      {editor.fields.map((field, index) => {
        const error = emptyTeamError(editor, index)
        const ids = editor.teams[index]?.playerIds ?? []
        return (
          <section key={field.id} className={cn("flex flex-col gap-2 rounded-xl border bg-card p-3", error && "border-destructive")}>
            <TeamHeader editor={editor} index={index} playersPerTeam={daily.playersPerTeam} />
            <ul className="flex flex-col gap-1">
              {ids.map((id) => {
                const player = editor.playersById.get(id)
                return player ? playerButton(player, index) : null
              })}
              {ids.length === 0 && <EmptyHint>Toque num jogador para trazê-lo para cá</EmptyHint>}
            </ul>
            {error && <p className="text-caption text-destructive">{error}</p>}
          </section>
        )
      })}
      {editor.canAddTeam && (
        <Button type="button" variant="outline" onClick={editor.appendTeam} className="gap-1.5">
          <HugeiconsIcon icon={Add01Icon} className="size-4" />
          Adicionar time
        </Button>
      )}

      <Drawer open={moving !== null} onOpenChange={(open) => !open && setMoving(null)}>
        <DrawerContent>
          <DrawerHeader className="text-left">
            <DrawerTitle>Mover {moving?.player.username} para…</DrawerTitle>
            <DrawerDescription>Escolha o time ou deixe sem time.</DrawerDescription>
          </DrawerHeader>
          <div className="flex flex-col gap-1 px-4 pb-6">
            {editor.fields.map((field, index) => (
              <Button
                key={field.id}
                type="button"
                variant={moving?.index === index ? "secondary" : "ghost"}
                onClick={() => pick(index)}
                className="h-11 justify-between rounded-lg"
              >
                <span className="flex min-w-0 items-center gap-2">
                  <TeamColorDot color={field.color} className="size-3" />
                  <span className="truncate">{editor.teams[index]?.name || `Time ${index + 1}`}</span>
                </span>
                <span className="text-muted-foreground">
                  {editor.teams[index]?.playerIds.length ?? 0}/{daily.playersPerTeam}
                </span>
              </Button>
            ))}
            <Button
              type="button"
              variant={moving?.index === null ? "secondary" : "ghost"}
              onClick={() => pick(null)}
              className="h-11 justify-between rounded-lg"
            >
              <span>Sem time</span>
              <span className="text-muted-foreground">{editor.unassigned.length}</span>
            </Button>
          </div>
        </DrawerContent>
      </Drawer>
    </div>
  )
}
