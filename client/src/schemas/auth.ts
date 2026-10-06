import { z } from "zod"

// RegisterRequestDTO.password and ResetPasswordRequestDTO.newPassword
export const PASSWORD_MIN_LENGTH = 8

// mirrors ForgotPasswordRequestDTO
export const forgotPasswordSchema = z.object({
  email: z.string().trim().min(1, "O e-mail é obrigatório").email("E-mail inválido"),
})
export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>

// mirrors ResetPasswordRequestDTO (the token comes from the link, not from the form); confirmPassword is client-only
export const resetPasswordSchema = z
  .object({
    newPassword: z
      .string()
      .min(1, "A senha é obrigatória")
      .min(PASSWORD_MIN_LENGTH, `A senha deve ter pelo menos ${PASSWORD_MIN_LENGTH} caracteres`),
    confirmPassword: z.string().min(1, "Confirme a nova senha"),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: "As senhas não coincidem",
    path: ["confirmPassword"],
  })
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>
