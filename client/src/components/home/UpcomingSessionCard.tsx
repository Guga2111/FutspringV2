import { Link } from "react-router-dom"
import { Check, MapPin } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { DailyStatusBadge } from "@/components/daily/DailyStatusBadge"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { cn } from "@/lib/utils"
import { formatCardDate } from "@/utils/home"
import type { NextDaily, PeladaResponse } from "@/types/pelada"

interface UpcomingSessionCardProps {
  pelada: PeladaResponse
  next: NextDaily
  pending: boolean
  onToggle: () => void
}

export function UpcomingSessionCard({ pelada, next, pending, onToggle }: UpcomingSessionCardProps) {
  const percent = next.capacity > 0 ? Math.min(100, (next.confirmedCount / next.capacity) * 100) : 0
  return (
    <article
      className={cn(
        "flex min-w-0 snap-start flex-col gap-3.5 rounded-tile border bg-card p-3.5",
        next.isConfirmed && "border-success-border",
      )}
    >
      <div className="flex min-w-0 items-center gap-2">
        <PeladaAvatar name={pelada.name} image={pelada.image} className="size-6 rounded-[7px] text-[9px]" />
        <span className="min-w-0 flex-1 truncate text-sm font-semibold">{pelada.name}</span>
        <DailyStatusBadge status={next.status} size="sm" className="shrink-0" />
      </div>
      <div className="flex min-w-0 flex-col gap-1">
        <div className="flex flex-wrap items-baseline gap-2">
          <span className="text-[22px] font-bold tracking-[-0.01em]">{formatCardDate(next.date)}</span>
          <span className="text-[15px] font-medium text-muted-foreground">{next.time}</span>
        </div>
        {pelada.address && (
          <span className="flex min-w-0 items-center gap-1.5 text-xs text-subtle-foreground">
            <MapPin className="size-3 shrink-0" />
            <span className="truncate">{pelada.address}</span>
          </span>
        )}
      </div>
      <div className="flex flex-col gap-1.5">
        <Progress value={percent} aria-label="Confirmados" className="h-1.5 bg-secondary" indicatorClassName="bg-gradient-primary" />
        <span className="text-xs text-muted-foreground">
          <span className="font-semibold text-foreground">{next.confirmedCount}</span> de {next.capacity} confirmados
        </span>
      </div>
      {next.isConfirmed ? (
        <div className="flex h-[38px] items-center justify-between gap-2 rounded-full bg-success-muted pl-3 pr-1.5">
          <span className="flex items-center gap-1.5 whitespace-nowrap text-[13px] font-semibold text-success">
            <Check className="size-3.5" strokeWidth={2.6} />
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
        <Button variant="gradient" disabled={pending} onClick={onToggle} className="h-[38px] w-full font-semibold">
          Confirmar presença
        </Button>
      )}
      <Link to={`/daily/${next.id}`} className="-mt-1 self-center text-[13px] text-muted-foreground transition-colors hover:text-foreground">
        Ver sessão →
      </Link>
    </article>
  )
}
