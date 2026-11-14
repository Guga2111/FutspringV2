import { z } from "@/lib/zod"

// mirrors CreateDailyRequestDTO (dailyDate is sent as yyyy-MM-dd)
export const createDailySchema = z.object({
  dailyDate: z
    .date()
    .nullable()
    .refine((date): date is Date => date !== null, "Escolha uma data"),
  dailyTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, "Horário inválido (HH:mm)"),
})
export type CreateDailyInput = z.input<typeof createDailySchema>
export type CreateDailyValues = z.output<typeof createDailySchema>

const scoreSchema = z.number().int().min(0, "O placar não pode ser negativo")

// One row per player of the two teams; only rows with goals or assists are sent
const playerStatRowSchema = z.object({
  userId: z.number().int(),
  username: z.string(),
  goals: z.number().int().min(0, "Gols não podem ser negativos"),
  assists: z.number().int().min(0, "Assistências não podem ser negativas"),
})

const matchRowSchema = z.object({
  // null: a new match
  matchId: z.number().int().nullable(),
  team1Id: z.number({ error: "Informe o time 1" }).int(),
  team2Id: z.number({ error: "Informe o time 2" }).int(),
  team1Score: scoreSchema,
  team2Score: scoreSchema,
  playerStats: z.array(playerStatRowSchema),
})

// mirrors List<MatchResultDTO> (POST /dailies/{id}/results): two different teams, scores ≥ 0,
// stats only for players of the match's two teams
export function makeResultsSchema(teams: { id: number; players: { id: number }[] }[]) {
  const playersByTeam = new Map(teams.map((t) => [t.id, new Set(t.players.map((p) => p.id))]))
  const inMatch = (teamId: number, userId: number) => playersByTeam.get(teamId)?.has(userId) ?? false
  return z.object({
    matches: z
      .array(
        matchRowSchema
          .refine((m) => m.team1Id !== m.team2Id, { message: "Escolha dois times diferentes", path: ["team2Id"] })
          .refine(
            (m) =>
              m.playerStats.every(
                (s) => (s.goals === 0 && s.assists === 0) || inMatch(m.team1Id, s.userId) || inMatch(m.team2Id, s.userId),
              ),
            { message: "Só jogadores dos dois times podem ter gols e assistências", path: ["playerStats"] },
          ),
      )
      .min(1, "Adicione pelo menos uma partida"),
  })
}
export type ResultsValues = z.infer<ReturnType<typeof makeResultsSchema>>
export type MatchRowValues = ResultsValues["matches"][number]

// Form rows -> request body (MatchResultInput[] in api/dailies.ts)
export function toMatchResultInputs(rows: MatchRowValues[]) {
  return rows.map((r) => ({
    matchId: r.matchId,
    team1Id: r.team1Id,
    team2Id: r.team2Id,
    team1Score: r.team1Score,
    team2Score: r.team2Score,
    playerStats: r.playerStats
      .filter((s) => s.goals > 0 || s.assists > 0)
      .map((s) => ({ userId: s.userId, goals: s.goals, assists: s.assists })),
  }))
}

// mirrors SaveTeamsRequestDTO (2–10 teams, name ≤ 30, optional #rrggbb color, positive player ids); the server
// also refuses an empty team, a player twice or a player who isn't confirmed
export const saveTeamsSchema = z.object({
  teams: z
    .array(
      z.object({
        name: z.string().trim().min(1, "O nome do time é obrigatório").max(30, "Máximo de 30 caracteres"),
        color: z
          .string()
          .regex(/^#[0-9a-fA-F]{6}$/, "A cor deve ser um hexadecimal de 6 dígitos (ex.: #3b82f6)")
          .nullable(),
        playerIds: z.array(z.number().int().positive()),
      }),
    )
    .min(2, "Monte de 2 a 10 times")
    .max(10, "Monte de 2 a 10 times")
    .superRefine((teams, ctx) => {
      teams.forEach((team, index) => {
        if (team.playerIds.length === 0) {
          ctx.addIssue({ code: "custom", path: [index, "playerIds"], message: `O ${team.name.trim() || "time"} está vazio` })
        }
      })
    }),
})
export type SaveTeamsValues = z.infer<typeof saveTeamsSchema>
