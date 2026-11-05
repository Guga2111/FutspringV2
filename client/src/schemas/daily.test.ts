import { describe, it, expect } from "vitest"
import { makeResultsSchema, saveTeamsSchema, toMatchResultInputs, type MatchRowValues } from "./daily"

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

describe("saveTeamsSchema", () => {
  const valid = {
    teams: [
      { name: "Time 1", color: "#3b82f6", playerIds: [1, 2] },
      { name: "Time 2", color: null, playerIds: [3] },
    ],
  }

  it("accepts teams of different sizes", () => {
    expect(saveTeamsSchema.safeParse(valid).success).toBe(true)
  })

  it("needs 2 to 10 teams", () => {
    expect(saveTeamsSchema.safeParse({ teams: valid.teams.slice(0, 1) }).success).toBe(false)
    const eleven = Array.from({ length: 11 }, (_, i) => ({ name: `T${i}`, color: null, playerIds: [i + 1] }))
    expect(saveTeamsSchema.safeParse({ teams: eleven }).success).toBe(false)
  })

  it("rejects a long name, an invalid color and an empty team", () => {
    const name = saveTeamsSchema.safeParse({ teams: [{ ...valid.teams[0], name: "x".repeat(31) }, valid.teams[1]] })
    const color = saveTeamsSchema.safeParse({ teams: [{ ...valid.teams[0], color: "azul" }, valid.teams[1]] })
    const empty = saveTeamsSchema.safeParse({ teams: [valid.teams[0], { ...valid.teams[1], playerIds: [] }] })
    expect(name.success).toBe(false)
    expect(color.success).toBe(false)
    expect(empty.success).toBe(false)
    expect(empty.error?.issues[0]).toMatchObject({ path: ["teams", 1, "playerIds"], message: "O Time 2 está vazio" })
  })
})
