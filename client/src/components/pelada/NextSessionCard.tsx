import { Link } from "react-router-dom"
import { CalendarX2, Check } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { DailyStatusBadge } from "@/components/daily/DailyStatusBadge"
import { parseLocalDate, weekdayShort } from "@/utils/dates"
import { formatRelativeSessionDay } from "@/utils/sessions"
import type { DailyListItem } from "@/types/daily"

const MONTHS_SHORT = ["JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ"] as const

interface NextSessionCardProps {
  daily: DailyListItem | null
  capacity: number
  isAdmin: boolean
  attendancePending: boolean
  onToggleAttendance: (daily: DailyListItem) => void
}

const label = "text-[11px] font-semibold uppercase tracking-[0.06em] text-subtle-foreground"

export function NextSessionCard({ daily, capacity, isAdmin, attendancePending, onToggleAttendance }: NextSessionCardProps) {
  if (!daily) {
    return (
      <section className="flex flex-wrap items-center gap-5 rounded-tile border border-dashed border-input p-[18px]">
        <div className="flex h-20 w-[72px] shrink-0 items-center justify-center rounded-xl border border-dashed border-input text-faint-foreground">
          <CalendarX2 className="size-[26px]" strokeWidth={1.8} />
        </div>
        <div className="flex min-w-[220px] flex-1 flex-col gap-1.5">
          <span className={label}>Próxima sessão</span>
          <span className="text-[17px] font-semibold">Nenhuma sessão agendada</span>
          <span className="text-[13px] text-muted-foreground">
            {isAdmin
              ? "Crie a próxima sessão ou ative a criação automática nas configurações da pelada."
              : "Quando um admin agendar a próxima sessão, ela aparece aqui."}
          </span>
        </div>
      </section>
    )
  }

  const date = parseLocalDate(daily.dailyDate)
  const percent = capacity > 0 ? Math.min(100, (daily.confirmedPlayerCount / capacity) * 100) : 0

  return (
    <section className="flex flex-wrap items-center gap-5 rounded-tile border bg-card p-[18px]">
      <div className="bg-gradient-date flex h-20 w-[72px] shrink-0 flex-col items-center justify-center rounded-xl text-white">
        <span className="text-on-brand-muted text-[11px] font-bold tracking-[0.08em]">{weekdayShort(daily.dailyDate).toUpperCase()}</span>
        <span className="text-[28px] font-extrabold leading-[1.1]">{String(date.getDate()).padStart(2, "0")}</span>
        <span className="text-on-brand-muted text-[11px] font-semibold">{MONTHS_SHORT[date.getMonth()]}</span>
      </div>
      <div className="flex min-w-[220px] flex-1 flex-col gap-2">
        <div className="flex items-center gap-2">
          <span className={label}>Próxima sessão</span>
          <DailyStatusBadge status={daily.status} />
        </div>
        <span className="text-lg font-semibold">
          {formatRelativeSessionDay(daily.dailyDate)} · {daily.dailyTime}
        </span>
        <div className="flex items-center gap-2.5">
          <Progress
            value={percent}
            aria-label="Confirmados"
            className="h-1.5 max-w-[260px] flex-1 bg-secondary"
            indicatorClassName="bg-gradient-primary"
          />
          <span className="text-[13px] text-muted-foreground">
            <span className="font-semibold text-foreground">{daily.confirmedPlayerCount}</span> de {capacity} confirmados
          </span>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Button asChild variant="outline" className="h-[38px] bg-transparent px-4 hover:bg-accent">
          <Link to={`/daily/${daily.id}`}>Ver sessão</Link>
        </Button>
        {daily.isConfirmed ? (
          <div className="flex h-[38px] items-center gap-2 rounded-full bg-success-muted pl-3 pr-1.5">
            <span className="flex items-center gap-1.5 text-[13px] font-semibold text-success">
              <Check className="size-3.5" strokeWidth={2.6} />
              Você vai
            </span>
            <Button
              variant="ghost"
              disabled={attendancePending}
              onClick={() => onToggleAttendance(daily)}
              className="h-7 px-2.5 text-xs font-normal text-success-soft hover:bg-success-border/40 hover:text-success-soft"
            >
              Desistir
            </Button>
          </div>
        ) : (
          <Button variant="gradient" disabled={attendancePending} onClick={() => onToggleAttendance(daily)} className="h-[38px] px-[18px] font-semibold">
            Confirmar presença
          </Button>
        )}
      </div>
    </section>
  )
}
