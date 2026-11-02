import type { MatchHistoryRow, StatsDTO, TimelinePoint } from "@/types/stats"
import { parseLocalDate } from "@/utils/dates"

// The API sends "W" (won the session), "L" (played without winning) or "—" (didn't play a match)
export type SessionResult = "W" | "L" | "—"

export const sessionResultLabel: Record<SessionResult, string> = {
  W: "Campeão",
  L: "Sem título",
  "—": "Não jogou",
}

export const sessionResultStyle: Record<SessionResult, string> = {
  W: "bg-highlight-muted text-highlight-text",
  L: "bg-muted text-muted-foreground",
  "—": "bg-transparent text-muted-foreground border-border",
}

export function toSessionResult(value: string): SessionResult {
  const upper = value.toUpperCase()
  return upper === "W" || upper === "L" ? upper : "—"
}

export interface ProfileRates {
  // match wins / matches played, 0–100
  winPct: number
  // sessions won / sessions played, 0–100
  titlePct: number
  goalsPerSession: number
  assistsPerSession: number
  contributionsPerSession: number
}

const ratio = (part: number, total: number) => (total > 0 ? part / total : 0)
// a share can't pass 100% even if the aggregates disagree
const pct = (part: number, total: number) => Math.min(100, Math.round(ratio(part, total) * 100))

export function profileRates(stats: StatsDTO): ProfileRates {
  const sessions = stats.sessionsPlayed ?? 0
  return {
    winPct: pct(stats.matchWins ?? 0, stats.matchesPlayed ?? 0),
    titlePct: pct(stats.wins, sessions),
    goalsPerSession: ratio(stats.goals, sessions),
    assistsPerSession: ratio(stats.assists, sessions),
    contributionsPerSession: ratio(stats.goals + stats.assists, sessions),
  }
}

// one decimal, pt-BR ("1,8")
export function formatAverage(value: number): string {
  return value.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })
}

// newest first
export function sortByDateDesc(rows: MatchHistoryRow[]): MatchHistoryRow[] {
  return [...rows].sort((a, b) => b.date.localeCompare(a.date) || b.dailyId - a.dailyId)
}

// the last n sessions, newest first
export function recentForm(rows: MatchHistoryRow[], n = 5): MatchHistoryRow[] {
  return sortByDateDesc(rows).slice(0, n)
}

export type TimelineRange = "7d" | "30d" | "90d"

export const timelineRangeLabel: Record<TimelineRange, string> = {
  "7d": "7 dias",
  "30d": "30 dias",
  "90d": "3 meses",
}

const RANGE_DAYS: Record<TimelineRange, number> = { "7d": 7, "30d": 30, "90d": 90 }

// points on or after (today − N days), in local dates, oldest first
export function filterTimeline(points: TimelinePoint[], range: TimelineRange, now: Date): TimelinePoint[] {
  const cutoff = new Date(now.getFullYear(), now.getMonth(), now.getDate() - RANGE_DAYS[range])
  return points
    .filter((point) => parseLocalDate(point.date) >= cutoff)
    .sort((a, b) => a.date.localeCompare(b.date))
}

export type TimelineMetric = "goals" | "assists" | "matchesPlayed"

export function timelineTotals(points: TimelinePoint[]): Record<TimelineMetric, number> {
  return points.reduce(
    (totals, point) => ({
      goals: totals.goals + point.goals,
      assists: totals.assists + point.assists,
      matchesPlayed: totals.matchesPlayed + point.matchesPlayed,
    }),
    { goals: 0, assists: 0, matchesPlayed: 0 },
  )
}

// the latest date of a list of ISO dates, or null
export function latestDate(dates: string[] | null | undefined): string | null {
  if (!dates || dates.length === 0) return null
  return [...dates].sort().at(-1) ?? null
}
