import { useImageUrl } from "@/hooks/useImageUrl"
import { useMemo, useState } from "react"
import { Link } from "react-router-dom"
import { format } from "date-fns"
import { ptBR } from "date-fns/locale"
import { HugeiconsIcon } from "@hugeicons/react"
import { ChampionIcon } from "@hugeicons/core-free-icons"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { DateRangeFilter } from "@/components/DateRangeFilter"

import { formatWeekdayDayMonth, parseLocalDate, type DateRange } from "@/utils/dates"
import {
  GALLERY_RESULTS,
  filterGallery,
  formatSessionCount,
  galleryCounts,
  galleryResultLabel,
  gallerySessions,
  type GalleryResult,
} from "@/utils/sessionGallery"
import { formatLongDate, formatSessionMeta } from "@/utils/sessions"
import type { DailyListItem } from "@/types/daily"

interface SessionGalleryProps {
  dailies: DailyListItem[]
  isLoading: boolean
}

// Finished sessions as champion photos, filtered by the caller's result and a date range
export function SessionGallery({ dailies, isLoading }: SessionGalleryProps) {
  const [result, setResult] = useState<GalleryResult>("ALL")
  const [range, setRange] = useState<DateRange | undefined>(undefined)

  const finished = useMemo(() => gallerySessions(dailies), [dailies])
  const counts = useMemo(() => galleryCounts(finished, range), [finished, range])
  const visible = useMemo(() => filterGallery(finished, { result, range }), [finished, result, range])
  const filtered = result !== "ALL" || range?.from !== undefined

  function clearFilters() {
    setResult("ALL")
    setRange(undefined)
  }

  function onResultChange(value: string) {
    if (value) setResult(value as GalleryResult)
  }

  return (
    <div className="grid gap-5 md:grid-cols-[200px_minmax(0,1fr)] md:gap-6">
      <ToggleGroup
        type="single"
        variant="nav"
        orientation="vertical"
        value={result}
        onValueChange={onResultChange}
        aria-label="Filtrar por resultado"
        className="hidden flex-col items-stretch gap-1 self-start md:flex"
      >
        {GALLERY_RESULTS.map((key) => (
          <ToggleGroupItem key={key} value={key}>
            {galleryResultLabel[key]}
            <span className="text-caption tabular-nums text-subtle-foreground">{counts[key]}</span>
          </ToggleGroupItem>
        ))}
      </ToggleGroup>

      <div className="flex min-w-0 flex-col gap-4">
        <ToggleGroup
          type="single"
          variant="chip"
          size="chip"
          value={result}
          onValueChange={onResultChange}
          aria-label="Filtrar por resultado"
          className="justify-start gap-1.5 md:hidden"
        >
          {GALLERY_RESULTS.map((key) => (
            <ToggleGroupItem key={key} value={key} className="group">
              {galleryResultLabel[key]}
              <span className="text-xs tabular-nums text-subtle-foreground group-data-[state=on]:text-primary-foreground/60">
                {counts[key]}
              </span>
            </ToggleGroupItem>
          ))}
        </ToggleGroup>

        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
          <h3 className="text-title font-semibold">{galleryResultLabel[result]}</h3>
          <div className="flex items-center gap-3">
            <DateRangeFilter
              value={range}
              onChange={setRange}
              defaultMonth={finished[0] ? parseLocalDate(finished[0].dailyDate) : undefined}
            />
            <span className="whitespace-nowrap text-body-sm tabular-nums text-subtle-foreground">
              {isLoading ? "…" : formatSessionCount(visible.length)}
            </span>
          </div>
        </div>

        {isLoading ? (
          <GalleryGrid>
            {Array.from({ length: 6 }, (_, i) => (
              <li key={i} className="flex flex-col gap-2.5">
                <Skeleton className="aspect-[4/3] w-full rounded-tile" />
                <Skeleton className="h-4 w-2/3" />
                <Skeleton className="h-3 w-1/2" />
              </li>
            ))}
          </GalleryGrid>
        ) : visible.length === 0 ? (
          <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-input p-10 text-center text-sm text-subtle-foreground">
            {finished.length === 0 ? "Nenhuma sessão finalizada ainda." : "Nenhuma sessão com esses filtros."}
            {filtered && finished.length > 0 && (
              <Button variant="outline" size="sm" onClick={clearFilters}>
                Limpar filtros
              </Button>
            )}
          </div>
        ) : (
          <GalleryGrid>
            {visible.map((daily) => (
              <SessionGalleryCard key={daily.id} daily={daily} />
            ))}
          </GalleryGrid>
        )}
      </div>
    </div>
  )
}

function GalleryGrid({ children }: { children: React.ReactNode }) {
  return <ul className="grid grid-cols-2 gap-x-3 gap-y-5 md:gap-x-4 md:gap-y-6 lg:grid-cols-3">{children}</ul>
}

function SessionGalleryCard({ daily }: { daily: DailyListItem }) {
  const photo = useImageUrl(daily.championImage)
  const date = parseLocalDate(daily.dailyDate)
  return (
    <li>
      <Link
        to={`/daily/${daily.id}`}
        className="group flex flex-col gap-2.5 rounded-tile outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
      >
        <div className="relative aspect-[4/3] overflow-hidden rounded-tile bg-card-elevated">
          {photo ? (
            <img
              src={photo}
              alt={`Campeões de ${formatLongDate(daily.dailyDate)}`}
              loading="lazy"
              width={480}
              height={360}
              className="size-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
              style={{ objectPosition: "center 55%" }}
            />
          ) : (
            <div className="flex size-full flex-col items-center justify-center gap-0.5 text-muted-foreground transition-colors group-hover:bg-row-hover">
              <span className="font-display text-display font-bold md:text-display-lg leading-none tabular-nums text-foreground">
                {format(date, "dd")}
              </span>
              <span className="text-body-sm">{format(date, "MMM yyyy", { locale: ptBR })}</span>
              <span className="mt-2 hidden text-caption text-subtle-foreground sm:block">Sem foto do campeão</span>
            </div>
          )}
          {daily.wonSession && (
            <Badge variant="status" className="absolute left-2.5 top-2.5 gap-1 bg-highlight text-highlight-foreground">
              <HugeiconsIcon icon={ChampionIcon} className="size-3.5" />
              Vitória
            </Badge>
          )}
        </div>
        <div className="flex min-w-0 flex-col gap-0.5 px-0.5">
          <span className="truncate text-body font-medium group-hover:underline group-hover:underline-offset-4">
            <span className="sm:hidden">{formatWeekdayDayMonth(daily.dailyDate)}</span>
            <span className="hidden sm:inline">{formatLongDate(daily.dailyDate)}</span>
          </span>
          <span className="truncate text-caption text-subtle-foreground">{formatSessionMeta(daily)}</span>
        </div>
      </Link>
    </li>
  )
}
