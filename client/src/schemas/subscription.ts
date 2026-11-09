import { z } from "zod"

// mirrors PixClaimRequestDTO (plan @NotNull)
export const pixClaimSchema = z.object({
  plan: z.enum(["MONTHLY", "ANNUAL"], { error: "Escolha o plano" }),
})
export type PixClaimValues = z.infer<typeof pixClaimSchema>
