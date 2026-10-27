import { z } from "zod"
import { EDITABLE_POSITIONS } from "@/types/user"

// mirrors UpdateProfileRequest ("" clears the position)
export const profileSchema = z.object({
  username: z.string().trim().min(3, "Mínimo de 3 caracteres").max(30, "Máximo de 30 caracteres"),
  position: z.union([z.enum(EDITABLE_POSITIONS), z.literal("")]),
})
export type ProfileValues = z.infer<typeof profileSchema>
