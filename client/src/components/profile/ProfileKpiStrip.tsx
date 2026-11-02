import { Card } from "@/components/ui/card"
import { cn } from "@/lib/utils"
import type { StatsDTO } from "@/types/stats"

interface ProfileKpiStripProps {
  stats: StatsDTO
}

// The four career numbers in one card split by dividers: 2×2 on a phone, one row from md
export function ProfileKpiStrip({ stats }: ProfileKpiStripProps) {
  const items = [
    { label: "Sessões", value: stats.sessionsPlayed ?? 0, tone: "text-foreground" },
    { label: "Títulos", value: stats.wins, tone: "text-highlight-text" },
    { label: "Gols", value: stats.goals, tone: "text-highlight-text" },
    { label: "Assistências", value: stats.assists, tone: "text-brand-text" },
  ]

  return (
    <Card className="overflow-hidden">
      <dl className="grid grid-cols-2 md:grid-cols-4">
        {items.map((item, index) => (
          <div
            key={item.label}
            className={cn(
              "flex flex-col gap-1 px-4 py-3.5 md:px-5 md:py-4",
              index % 2 === 1 && "border-l",
              index >= 2 && "border-t md:border-t-0",
              index === 2 && "md:border-l",
            )}
          >
            <dt className="text-body-sm text-muted-foreground">{item.label}</dt>
            <dd className={cn("font-display text-score font-bold tabular-nums md:text-score-lg", item.tone)}>
              {item.value}
            </dd>
          </div>
        ))}
      </dl>
    </Card>
  )
}
