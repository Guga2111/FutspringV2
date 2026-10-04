import { addDays, format } from "date-fns"
import { ptBR } from "date-fns/locale"
import { isDailyOpen, type DailyListItem } from "@/types/daily"
import { parseLocalDate } from "@/utils/dates"

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

function todayIso(now: Date): string {
  return format(now, "yyyy-MM-dd")
}

// The next session still open for attendance (SCHEDULED/CONFIRMED) from today on, the earliest first
export function pickNextSession(dailies: DailyListItem[], now = new Date()): DailyListItem | null {
  const today = todayIso(now)
  return (
    dailies
      .filter((d) => isDailyOpen(d.status) && d.dailyDate >= today)
      .sort((a, b) => a.dailyDate.localeCompare(b.dailyDate) || a.id - b.id)[0] ?? null
  )
}

export interface SessionMonth {
  key: string
  // "Setembro 2026"
  label: string
  dailies: DailyListItem[]
}

// Newest month first, newest session first inside each month
export function groupDailiesByMonth(dailies: DailyListItem[]): SessionMonth[] {
  const sorted = [...dailies].sort((a, b) => b.dailyDate.localeCompare(a.dailyDate) || b.id - a.id)
  const months: SessionMonth[] = []
  for (const daily of sorted) {
    const key = daily.dailyDate.slice(0, 7)
    let month = months[months.length - 1]
    if (!month || month.key !== key) {
      month = { key, label: capitalize(format(parseLocalDate(daily.dailyDate), "MMMM yyyy", { locale: ptBR })), dailies: [] }
      months.push(month)
    }
    month.dailies.push(daily)
  }
  return months
}

// "Domingo, 27 de setembro"
export function formatLongDate(iso: string): string {
  return capitalize(format(parseLocalDate(iso), "EEEE, d 'de' MMMM", { locale: ptBR }))
}

// "Hoje, domingo", "Amanhã, segunda" or "Quinta, 8 de outubro"
export function formatRelativeSessionDay(iso: string, now = new Date()): string {
  const date = parseLocalDate(iso)
  const weekday = format(date, "EEEE", { locale: ptBR })
  if (iso === todayIso(now)) return `Hoje, ${weekday}`
  if (iso === todayIso(addDays(now, 1))) return `Amanhã, ${weekday}`
  return capitalize(format(date, "EEEE, d 'de' MMMM", { locale: ptBR }))
}

// "08:00 · 4 times · 6 partidas" (counts left out when zero)
export function formatSessionMeta(daily: Pick<DailyListItem, "dailyTime" | "teamCount" | "matchCount">): string {
  const parts = [daily.dailyTime]
  if (daily.teamCount > 0) parts.push(`${daily.teamCount} ${daily.teamCount === 1 ? "time" : "times"}`)
  if (daily.matchCount > 0) parts.push(`${daily.matchCount} ${daily.matchCount === 1 ? "partida" : "partidas"}`)
  return parts.join(" · ")
}
