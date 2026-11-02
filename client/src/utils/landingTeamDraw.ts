// The landing page's team draw demo: the same rule as DailyTeamManagementService.sortTeams
// (players grouped by stars, shuffled inside each tier, then LPT: each player, highest stars
// first, goes to the non-full team with the lowest total; ties go to the first such team).

export interface DrawPlayer {
  id: number
  name: string
  stars: number
}

export interface DrawnTeam {
  players: DrawPlayer[]
  stars: number
}

// Fisher-Yates with an injected random, so the tests are deterministic
function shuffle<T>(items: T[], random: () => number): T[] {
  const result = [...items]
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    ;[result[i], result[j]] = [result[j], result[i]]
  }
  return result
}

export function drawTeams(players: DrawPlayer[], teamCount: number, random: () => number = Math.random): DrawnTeam[] {
  if (teamCount < 1) return []
  const perTeam = Math.ceil(players.length / teamCount)

  const tiers = new Map<number, DrawPlayer[]>()
  for (const player of players) tiers.set(player.stars, [...(tiers.get(player.stars) ?? []), player])
  const ordered = [...tiers.entries()]
    .sort(([a], [b]) => b - a)
    .flatMap(([, tier]) => shuffle(tier, random))

  const teams: DrawnTeam[] = Array.from({ length: teamCount }, () => ({ players: [], stars: 0 }))
  for (const player of ordered) {
    let target: DrawnTeam | null = null
    for (const team of teams) {
      if (team.players.length < perTeam && (target === null || team.stars < target.stars)) target = team
    }
    if (target === null) break
    target.players.push(player)
    target.stars += player.stars
  }
  return teams
}
