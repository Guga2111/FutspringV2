import { parseLocalDate, weekdayShort } from "@/utils/dates"
import type { StatsDTO } from "@/types/stats"
import type { PeladaResponse } from "@/types/pelada"

// "Bom dia" until noon, "Boa tarde" until 18h, then "Boa noite"
export function greeting(now: Date): string {
  const hour = now.getHours()
  if (hour < 12) return "Bom dia"
  if (hour < 18) return "Boa tarde"
  return "Boa noite"
}

const MONTHS = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"] as const

// "2026-10-08" -> "Qui, 08 out"
export function formatCardDate(iso: string): string {
  const date = parseLocalDate(iso)
  return `${weekdayShort(iso)}, ${String(date.getDate()).padStart(2, "0")} ${MONTHS[date.getMonth()]}`
}

// Peladas with a next session, earliest first
export function upcomingSessions(peladas: PeladaResponse[]) {
  return peladas
    .filter((p): p is PeladaResponse & { nextDaily: NonNullable<PeladaResponse["nextDaily"]> } => p.nextDaily != null)
    .sort((a, b) => a.nextDaily.date.localeCompare(b.nextDaily.date) || a.nextDaily.time.localeCompare(b.nextDaily.time))
}

export function pendingLine(pendingCount: number): string {
  if (pendingCount === 0) return "Presença confirmada em todas as próximas sessões."
  return pendingCount === 1
    ? "Você tem 1 sessão esperando sua confirmação."
    : `Você tem ${pendingCount} sessões esperando sua confirmação.`
}

const decimal = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export interface Kpi {
  label: string
  value: number
  sub: string
}

// "Seus números": totals over every pelada; wins are match wins (matchWins), not sessions won
export function statsKpis(stats: StatsDTO): Kpi[] {
  const perMatch = (n: number) => (stats.matchesPlayed > 0 ? decimal.format(n / stats.matchesPlayed) : decimal.format(0))
  const winRate = stats.matchesPlayed > 0 ? Math.round((stats.matchWins / stats.matchesPlayed) * 100) : 0
  return [
    { label: "Gols", value: stats.goals, sub: `${perMatch(stats.goals)} por partida` },
    { label: "Assistências", value: stats.assists, sub: `${perMatch(stats.assists)} por partida` },
    { label: "Partidas", value: stats.matchesPlayed, sub: `em ${stats.sessionsPlayed} ${stats.sessionsPlayed === 1 ? "sessão" : "sessões"}` },
    { label: "Vitórias", value: stats.matchWins, sub: `${winRate}% de aproveitamento` },
  ]
}
