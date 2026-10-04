import { useState } from "react"
import { Brush, ChevronDown, HandHelping, Target, ThumbsDown, Trophy, type LucideIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Skeleton } from "@/components/ui/skeleton"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { cn } from "@/lib/utils"
import type { AwardCategory, AwardWinner, PeladaAwards } from "@/types/pelada"

const ICON_MAP: Record<string, LucideIcon> = {
  ARTILHEIRO: Target,
  GARCOM: HandHelping,
  PUSKAS: Brush,
  BOLA_MURCHA: ThumbsDown,
}

// 2nd and 3rd place trophies; 4th on are dimmed
const PLACE_COLORS = ["text-silver", "text-bronze"] as const

interface AwardsTabProps {
  awards: PeladaAwards | null
  isLoading: boolean
}

function LeaderRow({ winner }: { winner: AwardWinner }) {
  return (
    <div className="bg-gradient-leader -mx-1.5 mb-1 mt-0.5 flex items-center gap-3 rounded-[10px] border border-gold/20 p-2.5">
      <Trophy className="size-[18px] shrink-0 text-gold" />
      <PlayerAvatar username={winner.username} image={winner.userImage} className="size-[38px] text-xs ring-2 ring-gold" />
      <div className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-base font-bold">{winner.username}</span>
        <span className="text-[11px] font-semibold uppercase tracking-[0.05em] text-gold">Líder</span>
      </div>
      <Badge variant="status" className="bg-gold px-2.5 py-[3px] text-sm font-bold tabular-nums text-gold-foreground">
        {winner.count}x
      </Badge>
    </div>
  )
}

function WinnerRow({ winner, place }: { winner: AwardWinner; place: number }) {
  return (
    <div className="flex items-center gap-3 border-b border-accent px-1 py-[9px]">
      <Trophy className={cn("size-4 shrink-0", PLACE_COLORS[place - 1] ?? "text-faint-foreground/70")} />
      <PlayerAvatar username={winner.username} image={winner.userImage} />
      <span className="min-w-0 flex-1 truncate text-sm font-medium">{winner.username}</span>
      <Badge variant="status" className="bg-secondary px-2 py-0.5 text-xs font-semibold tabular-nums text-foreground">
        {winner.count}x
      </Badge>
    </div>
  )
}

function CategoryCard({ category }: { category: AwardCategory }) {
  const [open, setOpen] = useState(false)
  const Icon = ICON_MAP[category.type] ?? Trophy
  const [leader, ...others] = category.topWinners
  const visible = others.slice(0, 2)
  const hidden = others.slice(2)

  return (
    <article className="relative flex flex-col gap-2.5 overflow-hidden rounded-xl border bg-card px-4 pb-2.5 pt-4">
      <Trophy aria-hidden className="pointer-events-none absolute bottom-2.5 right-3.5 size-16 text-foreground opacity-[0.07]" />
      <div className="flex items-start gap-2.5">
        <Icon className="mt-0.5 size-[18px] shrink-0 text-foreground/90" />
        <div className="flex flex-col gap-px">
          <h3 className="text-base font-semibold">{category.name}</h3>
          <p className="text-xs text-subtle-foreground">{category.description}</p>
        </div>
      </div>
      {!leader ? (
        <p className="py-2 text-sm text-subtle-foreground">Sem dados ainda.</p>
      ) : (
        <Collapsible open={open} onOpenChange={setOpen} className="flex flex-col">
          <LeaderRow winner={leader} />
          {visible.map((winner, idx) => (
            <WinnerRow key={winner.userId} winner={winner} place={idx + 1} />
          ))}
          <CollapsibleContent>
            {hidden.map((winner, idx) => (
              <WinnerRow key={winner.userId} winner={winner} place={idx + 3} />
            ))}
          </CollapsibleContent>
          {hidden.length > 0 && (
            <CollapsibleTrigger asChild>
              <Button
                variant="ghost"
                className="relative mt-2.5 h-7 gap-1.5 self-center px-3 text-xs font-normal text-muted-foreground hover:bg-accent hover:text-foreground"
              >
                <ChevronDown className={cn("size-3 transition-transform duration-150", open && "rotate-180")} />
                {open ? "Mostrar menos" : `Ver todos (${category.topWinners.length})`}
              </Button>
            </CollapsibleTrigger>
          )}
        </Collapsible>
      )}
    </article>
  )
}

export function AwardsTab({ awards, isLoading }: AwardsTabProps) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-[repeat(auto-fit,minmax(300px,1fr))] gap-3">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-[220px] rounded-xl" />
        ))}
      </div>
    )
  }

  if (!awards) {
    return <p className="text-sm text-subtle-foreground">Sem dados de prêmios ainda.</p>
  }

  return (
    <div className="flex flex-col gap-3">
      <span className="text-[13px] text-subtle-foreground">
        {awards.totalCategories} categorias · {awards.totalAwardsDistributed} prêmios distribuídos
      </span>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(300px,100%),1fr))] gap-3">
        {awards.categories.map((cat) => (
          <CategoryCard key={cat.type} category={cat} />
        ))}
      </div>
    </div>
  )
}
