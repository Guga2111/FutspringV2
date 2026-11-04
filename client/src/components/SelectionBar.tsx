import type { ReactNode } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Cancel01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"

interface SelectionBarProps {
  count: number
  // selected items hidden by the current search/filters
  outsideFilter: number
  // more on the first line, after the count (e.g. " · R$ 180,00")
  summary?: ReactNode
  // a second, smaller line under the count
  note?: ReactNode
  // a bulk request is running
  pending: boolean
  onExit: () => void
  // the actions, between the count and ✕
  children: ReactNode
}

// Sticky bar at the bottom of a list in selection mode: the count, the screen's actions and ✕ (Esc is handled by
// useSelection). Shared by the members grid and the finance charges
export function SelectionBar({ count, outsideFilter, summary, note, pending, onExit, children }: SelectionBarProps) {
  return (
    <div
      role="toolbar"
      aria-label="Ações em massa"
      aria-busy={pending}
      className="sticky bottom-4 z-10 flex items-center gap-2 rounded-hero border bg-card p-2 pl-4 shadow-panel"
    >
      <span className="flex min-w-0 flex-col text-sm">
        <span className="font-semibold">
          {count} {count === 1 ? "selecionado" : "selecionados"}
          {summary}
          {outsideFilter > 0 && <span className="font-normal text-subtle-foreground"> · {outsideFilter} fora do filtro</span>}
        </span>
        {note && <span className="truncate text-xs text-subtle-foreground">{note}</span>}
      </span>
      {children}
      <Button variant="ghost" size="icon" aria-label="Sair da seleção" onClick={onExit} className="size-9 shrink-0">
        <HugeiconsIcon icon={Cancel01Icon} className="size-4" />
      </Button>
    </div>
  )
}
