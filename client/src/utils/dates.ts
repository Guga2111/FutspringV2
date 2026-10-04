import { format } from "date-fns"

// API dates are "yyyy-MM-dd" in local time; `new Date(iso)` would read them as UTC
export function parseLocalDate(iso: string): Date {
  const [year, month, day] = iso.split("-").map(Number)
  return new Date(year, month - 1, day)
}

// date-fns' ptBR "EEE" gives "quinta"; the design uses three letters
const WEEKDAY_SHORT = ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"] as const

export function weekdayShort(iso: string): string {
  return WEEKDAY_SHORT[parseLocalDate(iso).getDay()]
}

// "2026-10-08" -> "Qui, 08/10"
export function formatWeekdayDayMonth(iso: string): string {
  return `${weekdayShort(iso)}, ${format(parseLocalDate(iso), "dd/MM")}`
}

// Sidebar line under a pelada: "Qui, 08/10 · 21:00" or "Sem sessão agendada"
export function formatNextSessionShort(next: { date: string; time: string } | null): string {
  return next ? `${formatWeekdayDayMonth(next.date)} · ${next.time}` : "Sem sessão agendada"
}
