import { format } from "date-fns"
import { HugeiconsIcon, type IconSvgElement } from "@hugeicons/react"
import { Agreement02Icon, Award01Icon, Medal01Icon, Target02Icon } from "@hugeicons/core-free-icons"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { cn } from "@/lib/utils"
import type { StatsDTO } from "@/types/stats"
import { parseLocalDate } from "@/utils/dates"
import { latestDate } from "@/utils/profile"

interface AwardsCardProps {
  stats: StatsDTO
}

interface AwardItem {
  label: string
  count: number
  icon: IconSvgElement
  tone: string
  note?: string
}

// The session awards in one card split by dividers: 2×2 on a phone, one row from md
export function AwardsCard({ stats }: AwardsCardProps) {
  const lastPuskas = latestDate(stats.puskasDates)
  const items: AwardItem[] = [
    { label: "Artilheiro", count: stats.artilheiroWins ?? 0, icon: Target02Icon, tone: "text-highlight-text" },
    { label: "Garçom", count: stats.garcomWins ?? 0, icon: Agreement02Icon, tone: "text-brand-text" },
    {
      label: "Puskás",
      count: stats.puskasDates?.length ?? 0,
      icon: Award01Icon,
      tone: "text-gold",
      note: lastPuskas ? `Último em ${format(parseLocalDate(lastPuskas), "dd/MM/yyyy")}` : undefined,
    },
    { label: "Bola Murcha", count: stats.wiltballWins ?? 0, icon: Medal01Icon, tone: "text-muted-foreground" },
  ]

  return (
    <Card className="overflow-hidden">
      <CardHeader className="p-4 pb-3 md:p-5 md:pb-3">
        <CardTitle className="text-title font-semibold">Prêmios</CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <dl className="grid grid-cols-2 border-t md:grid-cols-4">
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
              <dt className="flex items-center gap-1.5 text-body-sm text-muted-foreground">
                <HugeiconsIcon icon={item.icon} className={cn("size-4 shrink-0", item.tone)} />
                {item.label}
              </dt>
              <dd className={cn("font-display text-score font-bold tabular-nums", item.count === 0 && "text-muted-foreground")}>
                {item.count}
              </dd>
              {item.note && <dd className="text-caption text-muted-foreground">{item.note}</dd>}
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  )
}
