import { z } from "zod"

// mirrors UpdatePlayerStarsRequestDTO
export const playerStarsSchema = z.object({
  stars: z.number().int().min(1, "Mínimo de 1 estrela").max(5, "Máximo de 5 estrelas"),
})
export type PlayerStarsValues = z.infer<typeof playerStarsSchema>
