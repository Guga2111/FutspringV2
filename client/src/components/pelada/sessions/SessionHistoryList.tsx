import { useImageUrl } from "@/hooks/useImageUrl"
import { useMemo } from "react"
import { Link } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowRight01Icon, GridViewIcon, LeftToRightListBulletIcon, UserGroupIcon } from "@hugeicons/core-free-icons"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { SessionGallery } from "@/components/pelada/sessions/SessionGallery"
import { useSessionsView, type SessionsView } from "@/components/pelada/hooks/useSessionsView"
import { DailyStatusBadge } from "@/components/daily/DailyStatusBadge"

import { parseLocalDate, weekdayShort } from "@/utils/dates"
import { formatLongDate, formatSessionMeta, groupDailiesByMonth } from "@/utils/sessions"
import type { DailyListItem } from "@/types/daily"

interface SessionHistoryListProps {
  dailies: DailyListItem[]
  isLoading: boolean
}

// Every session except the next one: grouped by month (list) or the finished ones as photos (gallery)
export function SessionHistoryList({ dailies, isLoading }: SessionHistoryListProps) {
  const [view, setView] = useSessionsView()
  const months = useMemo(() => groupDailiesByMonth(dailies), [dailies])
  const finishedCount = dailies.filter((d) => d.status === "FINISHED").length
  const countLabel =
    finishedCount === dailies.length
      ? `${finishedCount} ${finishedCount === 1 ? "sessão finalizada" : "sessões finalizadas"}`
      : `${dailies.length} ${dailies.length === 1 ? "sessão" : "sessões"}`

  return (
    <section className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
        <h2 className="whitespace-nowrap text-[15px] font-semibold">
          Histórico <span className="text-[13px] font-normal text-subtle-foreground">· {countLabel}</span>
        </h2>
        <div className="flex items-center gap-2">
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            value={view}
            onValueChange={(value) => value && setView(value as SessionsView)}
            aria-label="Modo de visualização"
            className="gap-0.5"
          >
            <ToggleGroupItem value="list" aria-label="Ver em lista" className="h-[34px] min-w-[34px] px-2">
              <HugeiconsIcon icon={LeftToRightListBulletIcon} />
            </ToggleGroupItem>
            <ToggleGroupItem value="gallery" aria-label="Ver em galeria" className="h-[34px] min-w-[34px] px-2">
              <HugeiconsIcon icon={GridViewIcon} />
            </ToggleGroupItem>
          </ToggleGroup>
        </div>
      </div>

      {view === "gallery" ? (
        <SessionGallery dailies={dailies} isLoading={isLoading} />
      ) : isLoading ? (
        <Skeleton className="h-48 w-full rounded-xl" />
      ) : months.length === 0 ? (
        <div className="rounded-xl border border-dashed border-input p-10 text-center text-sm text-subtle-foreground">
          Nenhuma sessão ainda.
        </div>
      ) : (
        months.map((month) => (
          <div key={month.key} className="flex flex-col gap-2">
            <h3 className="text-caption font-semibold text-muted-foreground">{month.label}</h3>
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

export function SessionRow({ daily }: { daily: DailyListItem }) {
  const photo = useImageUrl(daily.championImage)
  return (
    <li className="border-b border-border/60 last:border-b-0">
      <Link to={`/daily/${daily.id}`} className="flex items-center gap-3.5 px-3.5 py-3 transition-colors hover:bg-row-hover">
        <div className="flex w-11 shrink-0 flex-col items-center">
          <span className="font-display text-heading font-bold tabular-nums">{String(parseLocalDate(daily.dailyDate).getDate()).padStart(2, "0")}</span>
          <span className="text-[11px] font-semibold uppercase text-subtle-foreground">{weekdayShort(daily.dailyDate)}</span>
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-[3px]">
          <span className="truncate text-sm font-medium">{formatLongDate(daily.dailyDate)}</span>
          <span className="truncate text-xs text-subtle-foreground">{formatSessionMeta(daily)}</span>
        </div>
        <DailyStatusBadge status={daily.status} />
        <span className="hidden w-11 items-center justify-end gap-[5px] text-[13px] text-muted-foreground md:flex">
          <HugeiconsIcon icon={UserGroupIcon} className="size-3.5" aria-label="Jogadores" />
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
          // keeps the column so the badges line up with rows that have a photo; nothing is drawn
          <span aria-hidden className="hidden size-10 shrink-0 md:block" />
        )}
        <HugeiconsIcon icon={ArrowRight01Icon} className="size-4 shrink-0 text-faint-foreground" />
      </Link>
    </li>
  )
}
