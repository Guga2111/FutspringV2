import { Brush, HandHelping, Target, ThumbsDown, type LucideIcon } from "lucide-react"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { cn } from "@/lib/utils"
import { bestStatLabel } from "@/utils/finishedSession"
import type { DailyDetail } from "@/types/daily"

interface AwardCardData {
  title: string
  icon: LucideIcon
  // icon tile background + icon color
  tone: string
  detail: string | null
  winnerIds: number[]
  winnerNames: string[]
}

function AwardCard({ award, daily }: { award: AwardCardData; daily: DailyDetail }) {
  const Icon = award.icon
  const players = new Map(daily.teams.flatMap((t) => t.players.map((p) => [p.id, p] as const)))
  const single = award.winnerIds.length === 1 ? players.get(award.winnerIds[0]) : undefined
  return (
    <article className="flex flex-col gap-3 rounded-xl border bg-card p-3.5">
      <div className="flex items-center gap-2">
        <span className={cn("flex size-7 shrink-0 items-center justify-center rounded-lg", award.tone)}>
          <Icon className="size-3.5" strokeWidth={2.4} />
        </span>
        <h3 className="text-[13px] font-semibold">{award.title}</h3>
        {award.detail && <span className="ml-auto text-xs text-subtle-foreground">{award.detail}</span>}
      </div>
      {award.winnerNames.length === 0 ? (
        <span className="text-sm text-subtle-foreground">Sem vencedor</span>
      ) : (
        <div className="flex items-center gap-2.5">
          {single ? (
            <PlayerAvatar username={single.username} image={single.image} className="size-9 text-xs" />
          ) : (
            <Avatar className="size-9 shrink-0 text-xs">
              <AvatarFallback className="bg-avatar-fallback font-semibold">+{award.winnerNames.length}</AvatarFallback>
            </Avatar>
          )}
          <span className="min-w-0 text-base font-semibold">{award.winnerNames.join(", ")}</span>
        </div>
      )}
    </article>
  )
}

export function DailyAwardsGrid({ daily }: { daily: DailyDetail }) {
  const { award } = daily
  if (!award) return null
  const cards: AwardCardData[] = [
    {
      title: "Artilheiro",
      icon: Target,
      tone: "bg-success-muted text-success-strong",
      detail: bestStatLabel(daily.playerStats, award.artilheiroWinnerIds, "goals"),
      winnerIds: award.artilheiroWinnerIds,
      winnerNames: award.artilheiroWinnerNames,
    },
    {
      title: "Garçom",
      icon: HandHelping,
      tone: "bg-info-muted text-info",
      detail: bestStatLabel(daily.playerStats, award.garcomWinnerIds, "assists"),
      winnerIds: award.garcomWinnerIds,
      winnerNames: award.garcomWinnerNames,
    },
    {
      title: "Puskás",
      icon: Brush,
      tone: "bg-status-live text-gold",
      detail: "gol mais bonito",
      winnerIds: award.puskasWinnerIds,
      winnerNames: award.puskasWinnerNames,
    },
    {
      title: "Bola Murcha",
      icon: ThumbsDown,
      tone: "bg-destructive-muted text-destructive",
      detail: "votação",
      winnerIds: award.wiltballWinnerIds,
      winnerNames: award.wiltballWinnerNames,
    },
  ]
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-base font-semibold">Prêmios</h2>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(210px,1fr))] gap-3">
        {cards.map((card) => (
          <AwardCard key={card.title} award={card} daily={daily} />
        ))}
      </div>
    </section>
  )
}
