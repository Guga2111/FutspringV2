import { z } from "zod"

// RegisterRequestDTO.password and ResetPasswordRequestDTO.newPassword
export const PASSWORD_MIN_LENGTH = 8

// mirrors LoginRequestDTO
export const loginSchema = z.object({
  email: z.string().trim().min(1, "O e-mail é obrigatório").email("E-mail inválido"),
  password: z.string().min(1, "A senha é obrigatória"),
})
export type LoginValues = z.infer<typeof loginSchema>

// mirrors RegisterRequestDTO; confirmPassword and terms are client-only
export const registerSchema = z
  .object({
    username: z
      .string()
      .trim()
      .min(1, "O nome de usuário é obrigatório")
      .min(3, "O nome de usuário deve ter entre 3 e 30 caracteres")
      .max(30, "O nome de usuário deve ter entre 3 e 30 caracteres"),
    email: z.string().trim().min(1, "O e-mail é obrigatório").email("E-mail inválido"),
    password: z
      .string()
      .min(1, "A senha é obrigatória")
      .min(PASSWORD_MIN_LENGTH, `A senha deve ter pelo menos ${PASSWORD_MIN_LENGTH} caracteres`),
    confirmPassword: z.string().min(1, "Confirme sua senha"),
    terms: z.boolean().refine((accepted) => accepted, "Aceite os termos de uso para criar a conta"),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: "As senhas não coincidem",
    path: ["confirmPassword"],
  })
export type RegisterValues = z.infer<typeof registerSchema>

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
