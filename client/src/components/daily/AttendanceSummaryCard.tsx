import { CalendarCheck, Check } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { attendanceHint } from "@/utils/attendance"
import type { DailyDetail } from "@/types/daily"

interface AttendanceSummaryCardProps {
  daily: DailyDetail
  isConfirmed: boolean
  pending: boolean
  onToggle: () => void
}

export function AttendanceSummaryCard({ daily, isConfirmed, pending, onToggle }: AttendanceSummaryCardProps) {
  const capacity = daily.numberOfTeams * daily.playersPerTeam
  const count = daily.confirmedPlayers.length
  return (
    <section className="flex flex-wrap items-center gap-5 rounded-tile border bg-card p-5">
      <div className="flex min-w-[240px] flex-1 flex-col gap-2.5">
        <span className="text-[11px] font-semibold uppercase tracking-[0.06em] text-subtle-foreground">Presença</span>
        <div className="flex items-baseline gap-2">
          <span className="text-[32px] font-bold tabular-nums">{count}</span>
          <span className="text-[15px] text-muted-foreground">de {capacity} confirmados</span>
        </div>
        <Progress
          value={capacity > 0 ? Math.min(100, (count / capacity) * 100) : 0}
          aria-label="Confirmados"
          className="h-2 max-w-[420px] bg-secondary"
          indicatorClassName="bg-gradient-primary"
        />
        <span className="text-[13px] text-muted-foreground">{attendanceHint(count, daily.numberOfTeams, daily.playersPerTeam)}</span>
      </div>
      <div className="flex flex-col items-start gap-2">
        {isConfirmed ? (
          <>
            <span className="flex items-center gap-1.5 text-sm font-semibold text-success">
              <Check className="size-4" strokeWidth={2.4} />
              Presença confirmada
            </span>
            <Button variant="outline" disabled={pending} onClick={onToggle} className="h-9 bg-transparent px-4 hover:bg-accent">
              Não vou mais
            </Button>
          </>
        ) : (
          <>
            <span className="text-[13px] text-muted-foreground">Você ainda não confirmou</span>
            <Button variant="gradient" disabled={pending} onClick={onToggle} className="h-[42px] gap-2 px-5 text-[15px] font-semibold">
              <CalendarCheck className="size-4" />
              Confirmar presença
            </Button>
          </>
        )}
      </div>
    </section>
  )
}
