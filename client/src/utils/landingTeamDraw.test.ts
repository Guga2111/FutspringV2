import { describe, expect, it } from "vitest"
import { drawTeams, type DrawPlayer } from "@/utils/landingTeamDraw"

const players: DrawPlayer[] = [5, 5, 4, 4, 4, 3, 3, 3, 2, 1].map((stars, i) => ({ id: i + 1, name: `P${i + 1}`, stars }))

// a fixed sequence instead of Math.random
function seeded(seed: number) {
  let state = seed
  return () => {
    state = (state * 1103515245 + 12345) % 2147483648
    return state / 2147483648
  }
}

describe("drawTeams", () => {
  it("places every player once in full teams", () => {
    const teams = drawTeams(players, 2, seeded(1))
    expect(teams.map((team) => team.players.length)).toEqual([5, 5])
    const ids = teams.flatMap((team) => team.players.map((player) => player.id)).sort((a, b) => a - b)
    expect(ids).toEqual(players.map((player) => player.id))
  })

  it("balances the stars like the backend's LPT", () => {
    for (let seed = 1; seed <= 20; seed++) {
      const [a, b] = drawTeams(players, 2, seeded(seed))
      expect(a.stars + b.stars).toBe(34)
      expect(Math.abs(a.stars - b.stars)).toBeLessThanOrEqual(2)
    }
  })

  it("puts the highest tier first, one per team", () => {
    const teams = drawTeams(players, 2, seeded(3))
    expect(teams.map((team) => team.players[0].stars)).toEqual([5, 5])
  })

  it("only shuffles inside a tier", () => {
    const draws = new Set(
      Array.from({ length: 20 }, (_, i) =>
        drawTeams(players, 2, seeded(i + 1))[0]
          .players.map((player) => player.id)
          .join(","),
      ),
    )
    expect(draws.size).toBeGreaterThan(1)
  })

  it("returns no teams for a zero count", () => {
    expect(drawTeams(players, 0)).toEqual([])
  })
})
