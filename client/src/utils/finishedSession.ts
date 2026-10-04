import type { DailyDetail, LeagueTableEntryDTO, MatchDTO, TeamDTO, UserDailyStatsDTO } from "@/types/daily"

export interface ChampionSummary {
  team: TeamDTO
  entry: LeagueTableEntryDTO
  played: number
  // points / (played × 3), 0–100
  winRate: number
}

// The 1st place of the final table and its numbers
export function championSummary(daily: DailyDetail): ChampionSummary | null {
  const entry = daily.leagueTable.find((e) => e.position === 1) ?? daily.leagueTable[0]
  const team = entry && daily.teams.find((t) => t.id === entry.teamId)
  if (!entry || !team) return null
  const played = entry.wins + entry.draws + entry.losses
  return { team, entry, played, winRate: played > 0 ? Math.round((entry.points / (played * 3)) * 100) : 0 }
}

// "Leal (2), Gone" for goals or assists in one match; "—" when nobody
export function matchStatNames(match: MatchDTO, teams: TeamDTO[], key: "goals" | "assists"): string {
  const names = new Map(teams.flatMap((t) => t.players.map((p) => [p.id, p.username] as const)))
  const list = match.playerStats
    .filter((s) => s[key] > 0)
    .map((s) => `${names.get(s.userId) ?? "?"}${s[key] > 1 ? ` (${s[key]})` : ""}`)
  return list.length > 0 ? list.join(", ") : "—"
}

export type PlayerSortKey = "goals" | "assists" | "matchesPlayed" | "wins"

// Highest first; ties keep the goals order
export function sortPlayerStats(stats: UserDailyStatsDTO[], key: PlayerSortKey): UserDailyStatsDTO[] {
  return [...stats].sort((a, b) => b[key] - a[key] || b.goals - a.goals || a.username.localeCompare(b.username, "pt-BR"))
}

// Award card detail: the winners' best number in the session ("5 gols", "4 assist.")
export function bestStatLabel(stats: UserDailyStatsDTO[], winnerIds: number[], key: "goals" | "assists"): string | null {
  const best = Math.max(0, ...stats.filter((s) => winnerIds.includes(s.userId)).map((s) => s[key]))
  if (best === 0) return null
  if (key === "goals") return `${best} ${best === 1 ? "gol" : "gols"}`
  return `${best} assist.`
}
