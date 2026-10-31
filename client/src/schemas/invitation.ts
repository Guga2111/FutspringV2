import { z } from "zod"

// Mirrors InvitePlayerRequestDTO; search is a separate, read-only user lookup.
export const invitePlayerSchema = z.object({
  userId: z.number().int().positive("Selecione um jogador"),
})
export type InvitePlayerValues = z.infer<typeof invitePlayerSchema>
