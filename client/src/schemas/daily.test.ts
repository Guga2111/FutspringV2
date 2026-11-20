import { describe, it, expect } from "vitest"
import {
  finalizeDailySchema,
  importMessageSchema,
  importResolutionSchema,
  makeResultsSchema,
  saveTeamsSchema,
  toMatchResultInputs,
  toPopulateInput,
  type MatchRowValues,
} from "./daily"

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

describe("finalizeDailySchema", () => {
  it("accepts winners and awards nobody won", () => {
    expect(finalizeDailySchema.safeParse({ puskasWinnerIds: [10, 11], wiltballWinnerIds: [] }).success).toBe(true)
  })

  it("rejects ids that aren't positive integers", () => {
    expect(finalizeDailySchema.safeParse({ puskasWinnerIds: [0], wiltballWinnerIds: [] }).success).toBe(false)
    expect(finalizeDailySchema.safeParse({ puskasWinnerIds: [], wiltballWinnerIds: [1.5] }).success).toBe(false)
  })
})

describe("importMessageSchema", () => {
  it("needs some text", () => {
    expect(importMessageSchema.safeParse({ message: "Azul\nLeal" }).success).toBe(true)
    const blank = importMessageSchema.safeParse({ message: "  \n " })
    expect(blank.error?.issues[0]).toMatchObject({ path: ["message"], message: "Cole a mensagem da sessão" })
  })
})

describe("importResolutionSchema", () => {
  const row = (rawName: string, matchedUserId: number | null, skipped = false) => ({
    rawName,
    totalGoals: 0,
    totalAssists: 0,
    matchedUserId,
    skipped,
  })

  it("accepts linked and skipped names", () => {
    const values = { teams: [{ players: [row("Leal", 1), row("Visitante", null, true)] }, { players: [row("Souto", 2)] }] }
    expect(importResolutionSchema.safeParse(values).success).toBe(true)
  })

  it("puts an unresolved name on its row", () => {
    const result = importResolutionSchema.safeParse({ teams: [{ players: [row("Leal", 1), row("Ferraz", null)] }] })
    expect(result.error?.issues[0]).toMatchObject({ path: ["teams", 0, "players", 1], message: "Escolha o jogador ou ignore" })
  })

  it("refuses the same member for two names, on the second row", () => {
    const result = importResolutionSchema.safeParse({ teams: [{ players: [row("Leal", 1)] }, { players: [row("Leal Jr", 1)] }] })
    expect(result.error?.issues[0]).toMatchObject({
      path: ["teams", 1, "players", 0],
      message: "Este jogador já está vinculado a Leal",
    })
  })
})

describe("toPopulateInput", () => {
  it("sends the parsed colors and matches with the linked players only", () => {
    const parsed = {
      teams: [
        { colorName: "Azul", colorHex: "#3b82f6", players: [] },
        { colorName: "Branco", colorHex: "#f8fafc", players: [] },
      ],
      matches: [{ team1ColorName: "Azul", team1Score: 2, team2ColorName: "Branco", team2Score: 0 }],
    }
    const values = {
      teams: [
        {
          players: [
            { rawName: "Leal", totalGoals: 3, totalAssists: 1, matchedUserId: 1, skipped: false },
            { rawName: "Visitante", totalGoals: 1, totalAssists: 0, matchedUserId: null, skipped: true },
          ],
        },
        { players: [{ rawName: "Pedrão", totalGoals: 0, totalAssists: 1, matchedUserId: 2, skipped: false }] },
      ],
    }
    expect(toPopulateInput(parsed, values)).toEqual({
      teams: [
        { colorName: "Azul", colorHex: "#3b82f6", players: [{ userId: 1, totalGoals: 3, totalAssists: 1 }] },
        { colorName: "Branco", colorHex: "#f8fafc", players: [{ userId: 2, totalGoals: 0, totalAssists: 1 }] },
      ],
      matches: [{ team1ColorName: "Azul", team1Score: 2, team2ColorName: "Branco", team2Score: 0 }],
    })
  })
})
