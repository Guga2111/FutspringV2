import { Link } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowRight01Icon, Location01Icon, Tick02Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { DailyStatusBadge } from "@/components/daily/DailyStatusBadge"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { SelfConfirmButton } from "@/components/SelfConfirmButton"
import { formatCardDate, spotsLabel } from "@/utils/home"
import type { NextDaily, PeladaResponse } from "@/types/pelada"
import { isWritable } from "@/utils/subscription"

interface UpcomingSessionCardProps {
  pelada: PeladaResponse
  next: NextDaily
  pending: boolean
  onToggle: () => void
}

export function UpcomingSessionCard({ pelada, next, pending, onToggle }: UpcomingSessionCardProps) {
  const percent = next.capacity > 0 ? Math.min(100, (next.confirmedCount / next.capacity) * 100) : 0
  return (
    <article className="flex min-w-0 snap-start flex-col gap-4 rounded-tile border bg-card p-4">
      <header className="flex min-w-0 items-center gap-2">
        <PeladaAvatar name={pelada.name} image={pelada.image} className="size-7 shrink-0 rounded-lg text-[10px]" />
        <span className="min-w-0 flex-1 truncate text-body font-semibold">{pelada.name}</span>
        <DailyStatusBadge status={next.status} size="sm" className="shrink-0" />
      </header>

      <div className="flex min-w-0 flex-col gap-1.5">
        <div className="flex items-end justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-1">
            <span className="font-display text-heading-lg font-bold leading-none">{formatCardDate(next.date)}</span>
            <span className="text-body text-muted-foreground">às {next.time}</span>
          </div>
          <div className="flex shrink-0 flex-col items-end gap-1">
            <span className="font-display text-heading-lg font-bold leading-none tabular-nums">
              {next.confirmedCount}
              <span className="text-title text-muted-foreground">/{next.capacity}</span>
            </span>
            <span className="text-caption text-muted-foreground">{spotsLabel(next.confirmedCount, next.capacity)}</span>
          </div>
        </div>
        {pelada.address && (
          <span className="flex min-w-0 items-center gap-1.5 text-caption text-subtle-foreground">
            <HugeiconsIcon icon={Location01Icon} className="size-3 shrink-0" />
            <span className="truncate">{pelada.address}</span>
          </span>
        )}
      </div>

      <Progress
        value={percent}
        aria-label={`${next.confirmedCount} de ${next.capacity} confirmados`}
        className="h-1.5 bg-secondary"
        indicatorClassName="bg-brand"
      />

      <footer className="mt-auto flex items-start gap-2">
        <div className="min-w-0 flex-1">
          {next.isConfirmed ? (
            <div className="flex h-10 items-center justify-between gap-2 rounded-full bg-success-muted pl-3.5 pr-1.5">
              <span className="flex items-center gap-1.5 whitespace-nowrap text-body-sm font-semibold text-success">
                <HugeiconsIcon icon={Tick02Icon} className="size-3.5" strokeWidth={2.6} />
                Você vai
              </span>
              <Button
                variant="ghost"
                disabled={pending}
                onClick={onToggle}
                className="h-7 px-2.5 text-xs font-normal text-success-soft hover:bg-success-border/40 hover:text-success-soft"
              >
                Desistir
              </Button>
            </div>
          ) : (
            <SelfConfirmButton
              confirmOpensAt={next.confirmOpensAt}
              confirmedCount={next.confirmedCount}
              capacity={next.capacity}
              timeZone={pelada.timeZone}
              pending={pending}
              onConfirm={onToggle}
              readOnly={!isWritable(pelada.subscription)}
              className="h-10 w-full"
            />
          )}
        </div>
        <Button variant="outline" size="icon" asChild className="shrink-0">
          <Link to={`/daily/${next.id}`} aria-label="Ver sessão">
            <HugeiconsIcon icon={ArrowRight01Icon} className="size-4" />
          </Link>
        </Button>
      </footer>
    </article>
  )
}
