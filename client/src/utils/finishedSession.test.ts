import { describe, it, expect } from "vitest"
import { bestStatLabel, championSummary, matchStatNames, sortPlayerStats } from "./finishedSession"
import type { DailyDetail, MatchDTO, TeamDTO, UserDailyStatsDTO } from "@/types/daily"

const teams: TeamDTO[] = [
  { id: 1, name: "Vermelho", totalStars: 0, averageStars: 0, color: "#ef4444", players: [{ id: 10, username: "Leal", image: null, stars: 3, position: null }] },
  { id: 2, name: "Azul", totalStars: 0, averageStars: 0, color: null, players: [{ id: 20, username: "Gone", image: null, stars: 3, position: null }] },
]

const stat = (userId: number, username: string, goals: number, assists: number, matchesPlayed = 6, wins = 3): UserDailyStatsDTO => ({
  userId, username, goals, assists, matchesPlayed, wins,
})

describe("championSummary", () => {
  it("returns the first place with its win rate", () => {
    const daily = {
      teams,
      leagueTable: [
        { teamId: 2, teamName: "Azul", position: 2, wins: 3, draws: 0, losses: 7, goalsFor: 8, goalsAgainst: 14, goalDiff: -6, points: 9 },
        { teamId: 1, teamName: "Vermelho", position: 1, wins: 7, draws: 0, losses: 3, goalsFor: 14, goalsAgainst: 8, goalDiff: 6, points: 21 },
      ],
    } as unknown as DailyDetail
    const summary = championSummary(daily)
    expect(summary?.team.name).toBe("Vermelho")
    expect(summary?.played).toBe(10)
    expect(summary?.winRate).toBe(70)
  })

  it("is null without a table", () => {
    expect(championSummary({ teams, leagueTable: [] } as unknown as DailyDetail)).toBeNull()
  })
})

describe("matchStatNames", () => {
  const match = { playerStats: [{ userId: 10, goals: 2, assists: 0 }, { userId: 20, goals: 0, assists: 1 }] } as MatchDTO
  it("lists goal scorers and assisters", () => {
    expect(matchStatNames(match, teams, "goals")).toBe("Leal (2)")
    expect(matchStatNames(match, teams, "assists")).toBe("Gone")
    expect(matchStatNames({ playerStats: [] } as unknown as MatchDTO, teams, "goals")).toBe("—")
  })
})

describe("sortPlayerStats", () => {
  it("sorts by the chosen column, then goals", () => {
    const rows = [stat(1, "A", 1, 4), stat(2, "B", 3, 0), stat(3, "C", 2, 4)]
    expect(sortPlayerStats(rows, "goals").map((r) => r.username)).toEqual(["B", "C", "A"])
    expect(sortPlayerStats(rows, "assists").map((r) => r.username)).toEqual(["C", "A", "B"])
  })
})

describe("bestStatLabel", () => {
  it("formats the winners' best number", () => {
    const rows = [stat(10, "Leal", 5, 1), stat(20, "Gone", 2, 4)]
    expect(bestStatLabel(rows, [10], "goals")).toBe("5 gols")
    expect(bestStatLabel(rows, [20], "assists")).toBe("4 assist.")
    expect(bestStatLabel(rows, [], "goals")).toBeNull()
  })
})
