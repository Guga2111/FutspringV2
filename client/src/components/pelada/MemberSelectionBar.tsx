import { useMemo, useState } from "react"
import type { LucideIcon } from "lucide-react"
import { CalendarCheck, CalendarX, ChevronDown, ListChecks, ShieldCheck, ShieldOff, Star, Trash2, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { ConfirmActionDialog } from "@/components/ConfirmActionDialog"
import { SetPlayerStarsDialog } from "@/components/pelada/SetPlayerStarsDialog"
import {
  bulkActionLabel,
  planBulkActions,
  pluralMembers,
  previewNames,
  removeSkipNote,
  sharedStars,
  type BulkMemberAction,
  type BulkPlanContext,
} from "@/utils/bulkMemberActions"
import type { PeladaMember } from "@/types/pelada"

interface MemberSelectionBarProps extends BulkPlanContext {
  selected: PeladaMember[]
  // selected members hidden by the current search/filters
  outsideFilter: number
  allVisibleSelected: boolean
  // a bulk request is running
  pending: boolean
  onToggleVisible: () => void
  onExit: () => void
  onAction: (action: BulkMemberAction, targets: PeladaMember[], stars?: number) => void
}

const ACTION_ICON: Record<BulkMemberAction, LucideIcon> = {
  makeAdmin: ShieldCheck,
  removeAdmin: ShieldOff,
  makeMensalista: CalendarCheck,
  makeDiarista: CalendarX,
  setStars: Star,
  remove: Trash2,
}

// Bulk actions on the selected members, at the bottom of the members tab. The dialogs (stars, remove confirmation)
// close before onAction, which runs the request in the background
export function MemberSelectionBar({
  selected,
  outsideFilter,
  allVisibleSelected,
  pending,
  onToggleVisible,
  onExit,
  onAction,
  creatorId,
  currentUserId,
  mensalistaCount,
  maxMensalistas,
}: MemberSelectionBarProps) {
  const [starsOpen, setStarsOpen] = useState(false)
  const [removeOpen, setRemoveOpen] = useState(false)
  const plan = useMemo(
    () => planBulkActions(selected, { creatorId, currentUserId, mensalistaCount, maxMensalistas }),
    [selected, creatorId, currentUserId, mensalistaCount, maxMensalistas],
  )
  const skipNote = removeSkipNote(selected, creatorId, currentUserId)
  const removeTargets = plan.remove.targets

  function run(action: BulkMemberAction) {
    if (action === "setStars") setStarsOpen(true)
    else if (action === "remove") setRemoveOpen(true)
    else onAction(action, plan[action].targets)
  }

  const count = (
    <span className="flex min-w-0 flex-col text-sm">
      <span className="font-semibold">
        {selected.length} {selected.length === 1 ? "selecionado" : "selecionados"}
        {outsideFilter > 0 && <span className="font-normal text-subtle-foreground"> · {outsideFilter} fora do filtro</span>}
      </span>
      {skipNote && removeTargets.length > 0 && (
        <span className="truncate text-xs text-subtle-foreground">
          Remover {removeTargets.length} de {selected.length} · {skipNote}
        </span>
      )}
    </span>
  )

  const visibleLabel = allVisibleSelected ? "Desmarcar visíveis" : "Selecionar visíveis"
  const exitButton = (
    <Button variant="ghost" size="icon" aria-label="Sair da seleção" onClick={onExit} className="size-9 shrink-0">
      <X className="size-4" />
    </Button>
  )

  function actionItem(action: BulkMemberAction) {
    const { targets, hint } = plan[action]
    const Icon = ACTION_ICON[action]
    const destructive = action === "remove"
    return (
      <DropdownMenuItem
        key={action}
        disabled={pending || hint !== null}
        onSelect={() => run(action)}
        className={destructive ? "text-destructive focus:bg-destructive-muted focus:text-destructive" : undefined}
      >
        <Icon className={destructive ? "mr-2 size-4" : "mr-2 size-4 text-muted-foreground"} />
        <span className="flex flex-col">
          {bulkActionLabel[action]} ({targets.length})
          {hint && <span className="text-xs text-muted-foreground">{hint}</span>}
        </span>
      </DropdownMenuItem>
    )
  }

  function menu(label: string, actions: BulkMemberAction[]) {
    return (
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" disabled={pending} className="h-9 gap-1 px-3.5">
            {label}
            <ChevronDown className="size-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="center" side="top" className="w-60 rounded-[10px] shadow-menu">
          <DropdownMenuGroup>{actions.map(actionItem)}</DropdownMenuGroup>
        </DropdownMenuContent>
      </DropdownMenu>
    )
  }

  return (
    <>
      <div
        role="toolbar"
        aria-label="Ações em massa"
        aria-busy={pending}
        className="sticky bottom-4 z-10 flex items-center gap-2 rounded-hero border bg-card p-2 pl-4 shadow-panel"
      >
        {count}

        {/* desktop: one control per group of actions */}
        <div className="ml-auto hidden items-center gap-2 md:flex">
          <Button variant="ghost" disabled={pending} onClick={onToggleVisible} className="h-9 gap-1.5 px-3.5">
            <ListChecks className="size-4" />
            {visibleLabel}
          </Button>
          {menu("Admin", ["makeAdmin", "removeAdmin"])}
          {menu("Tipo", ["makeMensalista", "makeDiarista"])}
          <Button variant="outline" disabled={pending} onClick={() => run("setStars")} className="h-9 gap-1.5 px-3.5">
            <Star className="size-4" />
            Estrelas
          </Button>
          <Button
            variant="destructive"
            disabled={pending || plan.remove.hint !== null}
            onClick={() => run("remove")}
            className="h-9 gap-1.5 px-3.5"
          >
            <Trash2 className="size-4" />
            Remover ({removeTargets.length})
          </Button>
          {exitButton}
        </div>

        {/* below md: every action in one menu */}
        <div className="ml-auto flex items-center gap-1 md:hidden">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" disabled={pending} className="h-9 gap-1 px-3.5">
                Ações
                <ChevronDown className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" side="top" className="w-64 rounded-[10px] shadow-menu">
              <DropdownMenuGroup>
                <DropdownMenuItem disabled={pending} onSelect={onToggleVisible}>
                  <ListChecks className="mr-2 size-4 text-muted-foreground" />
                  {visibleLabel}
                </DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator className="bg-border" />
              <DropdownMenuGroup>
                {(["makeAdmin", "removeAdmin", "makeMensalista", "makeDiarista", "setStars"] as const).map(actionItem)}
              </DropdownMenuGroup>
              <DropdownMenuSeparator className="bg-border" />
              <DropdownMenuGroup>{actionItem("remove")}</DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>
          {exitButton}
        </div>
      </div>

      {starsOpen && (
        <SetPlayerStarsDialog
          title={`Definir estrelas de ${pluralMembers(selected.length)}`}
          description="O mesmo nível para todos nesta pelada, usado para equilibrar os times no sorteio."
          defaultStars={sharedStars(selected) ?? 3}
          onClose={() => setStarsOpen(false)}
          onSave={(stars) => onAction("setStars", selected, stars)}
        />
      )}

      {removeOpen && (
        <ConfirmActionDialog
          title={`Remover ${pluralMembers(removeTargets.length)}?`}
          description={
            <>
              <p className="text-foreground">{previewNames(removeTargets)}</p>
              <p>Eles saem das sessões que ainda não começaram.</p>
              {skipNote && (
                <p>
                  Remover {removeTargets.length} de {selected.length} · {skipNote}.
                </p>
              )}
            </>
          }
          confirmLabel="Remover"
          pendingLabel="Removendo..."
          loading={false}
          onConfirm={() => {
            setRemoveOpen(false)
            onAction("remove", removeTargets)
          }}
          onClose={() => setRemoveOpen(false)}
        />
      )}
    </>
  )
}
