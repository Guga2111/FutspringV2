import { describe, it, expect } from "vitest"
import { formatGoalDiff, goalCheck, playerTotals, scorersLine, suggestPairing } from "./liveSession"
import type { MatchDTO, TeamDTO } from "@/types/daily"

const match = (id: number, stats: MatchDTO["playerStats"]): MatchDTO => ({
  id,
  team1Id: 1,
  team1Name: "Vermelho",
  team2Id: 2,
  team2Name: "Azul",
  team1Score: 2,
  team2Score: 1,
  winnerId: 1,
  playerStats: stats,
})

const teams: TeamDTO[] = [
  { id: 1, name: "Vermelho", totalStars: 0, averageStars: 0, color: null, players: [{ id: 10, username: "Leal", image: null, stars: 3, position: null }] },
  { id: 2, name: "Azul", totalStars: 0, averageStars: 0, color: null, players: [{ id: 20, username: "Gone", image: null, stars: 3, position: null }] },
]

describe("playerTotals", () => {
  it("sums goals and assists across matches", () => {
    const totals = playerTotals([
      match(1, [{ userId: 10, goals: 2, assists: 0 }]),
      match(2, [{ userId: 10, goals: 1, assists: 1 }, { userId: 20, goals: 0, assists: 2 }]),
    ])
    expect(totals.get(10)).toEqual({ goals: 3, assists: 1 })
    expect(totals.get(20)).toEqual({ goals: 0, assists: 2 })
  })
})

describe("scorersLine", () => {
  it("lists scorers with counts above one", () => {
    expect(scorersLine(match(1, [{ userId: 10, goals: 2, assists: 0 }, { userId: 20, goals: 1, assists: 0 }]), teams)).toBe(
      "Gols: Leal (2), Gone",
    )
    expect(scorersLine(match(1, [{ userId: 20, goals: 0, assists: 1 }]), teams)).toBe("Sem gols atribuídos")
  })
})

describe("suggestPairing", () => {
  it("follows the 4-team rotation and wraps around", () => {
    expect([0, 1, 2, 3, 4, 5, 6].map((i) => suggestPairing(4, i))).toEqual([
      [0, 1], [2, 3], [0, 2], [1, 3], [0, 3], [1, 2], [0, 1],
    ])
  })

  it("uses every pair for other team counts", () => {
    expect([0, 1, 2, 3].map((i) => suggestPairing(3, i))).toEqual([[0, 1], [0, 2], [1, 2], [0, 1]])
    expect(suggestPairing(2, 5)).toEqual([0, 1])
  })
})

describe("goalCheck", () => {
  it("is ok when the assigned goals match a non-zero score", () => {
    expect(goalCheck(2, 1, 2, 1)).toEqual({ state: "ok", label: "Gols atribuídos: 2/2 · 1/1" })
  })
  it("is neutral on 0–0", () => {
    expect(goalCheck(0, 0, 0, 0).state).toBe("empty")
  })
  it("warns on mismatch or unassigned goals", () => {
    expect(goalCheck(2, 1, 1, 1).state).toBe("mismatch")
    expect(goalCheck(2, 1, 0, 0)).toEqual({ state: "mismatch", label: "Gols não atribuídos" })
  })
})

describe("formatGoalDiff", () => {
  it("prefixes positive differences", () => {
    expect([3, 0, -2].map(formatGoalDiff)).toEqual(["+3", "0", "-2"])
  })
})
