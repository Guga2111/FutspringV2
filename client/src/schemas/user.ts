import { z } from "zod"
import { EDITABLE_POSITIONS } from "@/types/user"

// mirrors UpdateProfileRequest ("" clears the position)
export const profileSchema = z.object({
  username: z.string().trim().min(3, "Mínimo de 3 caracteres").max(30, "Máximo de 30 caracteres"),
  position: z.union([z.enum(EDITABLE_POSITIONS), z.literal("")]),
})
export type ProfileValues = z.infer<typeof profileSchema>

// mirrors ChangePasswordRequestDTO; confirmPassword is client-only
export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Informe a senha atual"),
    newPassword: z.string().min(8, "A senha deve ter pelo menos 8 caracteres"),
    confirmPassword: z.string(),
  })
  .refine((values) => values.confirmPassword === values.newPassword, {
    message: "As senhas não coincidem",
    path: ["confirmPassword"],
  })
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>

// mirrors DeleteAccountRequestDTO
export const deleteAccountSchema = z.object({
  password: z.string().min(1, "Informe a sua senha"),
})
export type DeleteAccountValues = z.infer<typeof deleteAccountSchema>
