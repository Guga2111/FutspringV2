import type { MatchDTO, TeamDTO } from "@/types/daily"

export interface PlayerTotals {
  goals: number
  assists: number
}

// Goals and assists per player over the saved matches
export function playerTotals(matches: MatchDTO[]): Map<number, PlayerTotals> {
  const totals = new Map<number, PlayerTotals>()
  for (const match of matches) {
    for (const stat of match.playerStats) {
      const current = totals.get(stat.userId) ?? { goals: 0, assists: 0 }
      totals.set(stat.userId, { goals: current.goals + stat.goals, assists: current.assists + stat.assists })
    }
  }
  return totals
}

function playerNames(teams: TeamDTO[]): Map<number, string> {
  return new Map(teams.flatMap((t) => t.players.map((p) => [p.id, p.username] as const)))
}

// "Gols: Leal (2), Gone" or "Sem gols atribuídos"
export function scorersLine(match: MatchDTO, teams: TeamDTO[]): string {
  const names = playerNames(teams)
  const scorers = match.playerStats
    .filter((s) => s.goals > 0)
    .map((s) => `${names.get(s.userId) ?? "?"}${s.goals > 1 ? ` (${s.goals})` : ""}`)
  return scorers.length > 0 ? `Gols: ${scorers.join(", ")}` : "Sem gols atribuídos"
}

// Rotation of matchups when adding a match; the 4-team order spreads rest evenly (design handoff)
const FOUR_TEAMS: [number, number][] = [
  [0, 1],
  [2, 3],
  [0, 2],
  [1, 3],
  [0, 3],
  [1, 2],
]

export function suggestPairing(teamCount: number, matchIndex: number): [number, number] {
  if (teamCount < 2) return [0, 0]
  const pairs: [number, number][] = []
  if (teamCount === 4) {
    pairs.push(...FOUR_TEAMS)
  } else {
    for (let i = 0; i < teamCount; i++) for (let j = i + 1; j < teamCount; j++) pairs.push([i, j])
  }
  return pairs[matchIndex % pairs.length]
}

export type GoalCheckState = "ok" | "empty" | "mismatch"

// Assigned goals vs the score. Only a warning: the form still saves (the API rejects sums above the score)
export function goalCheck(score1: number, score2: number, goals1: number, goals2: number): { state: GoalCheckState; label: string } {
  if (goals1 + goals2 === 0 && score1 + score2 > 0) return { state: "mismatch", label: "Gols não atribuídos" }
  const label = `Gols atribuídos: ${goals1}/${score1} · ${goals2}/${score2}`
  if (goals1 !== score1 || goals2 !== score2) return { state: "mismatch", label }
  return { state: score1 + score2 > 0 ? "ok" : "empty", label }
}

// Goal difference: "+3", "0", "-2", colored green / neutral / red
export function formatGoalDiff(diff: number): string {
  return diff > 0 ? `+${diff}` : String(diff)
}

export function goalDiffClass(diff: number): string {
  return diff > 0 ? "text-success" : diff < 0 ? "text-destructive-soft" : "text-muted-foreground"
}
