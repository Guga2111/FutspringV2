import type { DailyDetail } from '../types/daily'

export function buildPlayerStats(
  daily: DailyDetail,
  team1Id: number,
  team2Id: number,
  existing: { userId: number; goals: number; assists: number }[] = [],
) {
  const statMap = new Map(existing.map((s) => [s.userId, s]))
  const teamPlayers = daily.teams
    .filter((t) => t.id === team1Id || t.id === team2Id)
    .flatMap((t) => t.players)
  return teamPlayers.map((p) => {
    const s = statMap.get(p.id)
    return { userId: p.id, username: p.username, goals: s?.goals ?? 0, assists: s?.assists ?? 0 }
  })
}
