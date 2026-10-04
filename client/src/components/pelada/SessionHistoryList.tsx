import { useMemo } from "react"
import { Link } from "react-router-dom"
import { ChevronRight, Plus, Users } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { DailyStatusBadge } from "@/components/daily/DailyStatusBadge"
import { getFileUrl } from "@/lib/utils"
import { parseLocalDate, weekdayShort } from "@/utils/dates"
import { formatLongDate, formatSessionMeta, groupDailiesByMonth } from "@/utils/sessions"
import type { DailyListItem } from "@/types/daily"

interface SessionHistoryListProps {
  dailies: DailyListItem[]
  isLoading: boolean
  isAdmin: boolean
  onOpenCreate: () => void
}

// Every session except the next one, grouped by month
export function SessionHistoryList({ dailies, isLoading, isAdmin, onOpenCreate }: SessionHistoryListProps) {
  const months = useMemo(() => groupDailiesByMonth(dailies), [dailies])
  const finishedCount = dailies.filter((d) => d.status === "FINISHED").length
  const countLabel =
    finishedCount === dailies.length
      ? `${finishedCount} ${finishedCount === 1 ? "sessão finalizada" : "sessões finalizadas"}`
      : `${dailies.length} ${dailies.length === 1 ? "sessão" : "sessões"}`

  return (
    <section className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-[15px] font-semibold">
          Histórico <span className="text-[13px] font-normal text-subtle-foreground">· {countLabel}</span>
        </h2>
        {isAdmin && (
          <Button variant="outline" onClick={onOpenCreate} className="h-[34px] gap-1.5 bg-transparent px-3.5 hover:bg-accent">
            <Plus className="size-3.5" strokeWidth={2.2} />
            Nova sessão
          </Button>
        )}
      </div>

      {isLoading ? (
        <Skeleton className="h-48 w-full rounded-xl" />
      ) : months.length === 0 ? (
        <div className="rounded-xl border border-dashed border-input p-10 text-center text-sm text-subtle-foreground">
          Nenhuma sessão ainda.
        </div>
      ) : (
        months.map((month) => (
          <div key={month.key} className="flex flex-col gap-2">
            <h3 className="text-xs font-semibold uppercase tracking-[0.06em] text-faint-foreground">{month.label}</h3>
            <ul className="overflow-hidden rounded-xl border">
              {month.dailies.map((daily) => (
                <SessionRow key={daily.id} daily={daily} />
              ))}
            </ul>
          </div>
        ))
      )}
    </section>
  )
}

function SessionRow({ daily }: { daily: DailyListItem }) {
  const photo = getFileUrl(daily.championImage)
  return (
    <li className="border-b border-border/60 last:border-b-0">
      <Link to={`/daily/${daily.id}`} className="flex items-center gap-3.5 px-3.5 py-3 transition-colors hover:bg-row-hover">
        <div className="flex w-11 shrink-0 flex-col items-center">
          <span className="text-xl font-bold leading-[1.1]">{String(parseLocalDate(daily.dailyDate).getDate()).padStart(2, "0")}</span>
          <span className="text-[11px] font-semibold uppercase text-subtle-foreground">{weekdayShort(daily.dailyDate)}</span>
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-[3px]">
          <span className="truncate text-sm font-medium">{formatLongDate(daily.dailyDate)}</span>
          <span className="truncate text-xs text-subtle-foreground">{formatSessionMeta(daily)}</span>
        </div>
        <DailyStatusBadge status={daily.status} />
        <span className="hidden w-11 items-center justify-end gap-[5px] text-[13px] text-muted-foreground md:flex">
          <Users className="size-3.5" aria-label="Jogadores" />
          {daily.confirmedPlayerCount}
        </span>
        {photo ? (
          <img
            src={photo}
            alt="Foto do campeão"
            loading="lazy"
            width={40}
            height={40}
            className="hidden size-10 shrink-0 rounded-lg border border-input object-cover md:block"
            style={{ objectPosition: "center 55%" }}
          />
        ) : (
          <span title="Sem foto do campeão" className="hidden size-10 shrink-0 rounded-lg border border-dashed border-input md:block" />
        )}
        <ChevronRight className="size-4 shrink-0 text-faint-foreground" />
      </Link>
    </li>
  )
}
