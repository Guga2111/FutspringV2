import { describe, it, expect } from "vitest"
import { makeResultsSchema, toMatchResultInputs, type MatchRowValues } from "./daily"

const teams = [
  { id: 1, players: [{ id: 10 }, { id: 11 }] },
  { id: 2, players: [{ id: 20 }] },
  { id: 3, players: [{ id: 30 }] },
]
const schema = makeResultsSchema(teams)

const row = (patch: Partial<MatchRowValues> = {}): MatchRowValues => ({
  matchId: null,
  team1Id: 1,
  team2Id: 2,
  team1Score: 2,
  team2Score: 1,
  playerStats: [
    { userId: 10, username: "Leal", goals: 2, assists: 0 },
    { userId: 11, username: "Lui", goals: 0, assists: 0 },
    { userId: 20, username: "Gone", goals: 1, assists: 0 },
  ],
  ...patch,
})

describe("makeResultsSchema", () => {
  it("accepts a valid match", () => {
    expect(schema.safeParse({ matches: [row()] }).success).toBe(true)
  })

  it("rejects the same team on both sides", () => {
    const result = schema.safeParse({ matches: [row({ team2Id: 1 })] })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0].path).toEqual(["matches", 0, "team2Id"])
  })

  it("rejects negative scores and an empty list", () => {
    expect(schema.safeParse({ matches: [row({ team1Score: -1 })] }).success).toBe(false)
    expect(schema.safeParse({ matches: [] }).success).toBe(false)
  })

  it("rejects stats for a player of another team", () => {
    const stats = [{ userId: 30, username: "Outro", goals: 1, assists: 0 }]
    expect(schema.safeParse({ matches: [row({ playerStats: stats })] }).success).toBe(false)
  })
})

describe("toMatchResultInputs", () => {
  it("sends only players with goals or assists", () => {
    expect(toMatchResultInputs([row({ matchId: 5 })])).toEqual([
      {
        matchId: 5,
        team1Id: 1,
        team2Id: 2,
        team1Score: 2,
        team2Score: 1,
        playerStats: [
          { userId: 10, goals: 2, assists: 0 },
          { userId: 20, goals: 1, assists: 0 },
        ],
      },
    ])
  })
})
