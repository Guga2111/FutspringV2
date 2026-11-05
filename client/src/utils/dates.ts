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

// "2026-11-10" -> "10/11", with the year only outside the current one ("10/11/2025"): due dates, payments, awards
export function formatShortDate(iso: string, now: Date = new Date()): string {
  const date = parseLocalDate(iso)
  return format(date, date.getFullYear() === now.getFullYear() ? "dd/MM" : "dd/MM/yyyy")
}

// Sidebar line under a pelada: "Qui, 08/10 · 21:00" or "Sem sessão agendada"
export function formatNextSessionShort(next: { date: string; time: string } | null): string {
  return next ? `${formatWeekdayDayMonth(next.date)} · ${next.time}` : "Sem sessão agendada"
}

// "sáb, 31/10 às 20:00": an ISO instant shown in the pelada's zone (same text as the backend's error messages)
export function formatInZone(iso: string, timeZone: string): string {
  const parts = new Intl.DateTimeFormat("pt-BR", {
    timeZone,
    weekday: "short",
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(iso))
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? ""
  const weekday = part("weekday").replace(".", "").toLocaleLowerCase("pt-BR")
  return `${weekday}, ${part("day")}/${part("month")} às ${part("hour")}:${part("minute")}`
}

// A calendar range (react-day-picker's DateRange shape); `to` missing means a single day
export interface DateRange {
  from?: Date
  to?: Date
}

// "Todo o período", "08/10/2026", "01/10 – 31/10/2026" or "15/12/2025 – 10/01/2026"
export function formatRangeLabel(range: DateRange | undefined): string {
  if (!range?.from) return "Todo o período"
  const { from } = range
  const to = range.to ?? from
  if (format(from, "yyyy-MM-dd") === format(to, "yyyy-MM-dd")) return format(from, "dd/MM/yyyy")
  if (from.getFullYear() === to.getFullYear()) return `${format(from, "dd/MM")} – ${format(to, "dd/MM/yyyy")}`
  return `${format(from, "dd/MM/yyyy")} – ${format(to, "dd/MM/yyyy")}`
}

// Today as "yyyy-MM-dd" in a time zone (the pelada's): "en-CA" formats dates in ISO order
export function todayInZone(timeZone: string, now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(now)
}
