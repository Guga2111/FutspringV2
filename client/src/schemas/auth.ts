import { z } from "zod"

// RegisterRequestDTO.password, ResetPasswordRequestDTO.newPassword and ChangePasswordRequestDTO.newPassword
export const PASSWORD_MIN_LENGTH = 8
// BcryptLength: BCrypt takes 72 bytes in UTF-8, on every password the API hashes or compares
export const PASSWORD_MAX_BYTES = 72
const PASSWORD_TOO_LONG = "A senha é longa demais (até 72 caracteres; letras com acento contam em dobro)"
const utf8 = new TextEncoder()

// a password the API compares (login, current password, account deletion)
export function passwordField(requiredMessage: string) {
  return z
    .string()
    .min(1, requiredMessage)
    .refine((value) => utf8.encode(value).length <= PASSWORD_MAX_BYTES, PASSWORD_TOO_LONG)
}

// a password the API stores (registration, reset, change)
export function newPasswordField(requiredMessage: string) {
  return passwordField(requiredMessage).min(
    PASSWORD_MIN_LENGTH,
    `A senha deve ter pelo menos ${PASSWORD_MIN_LENGTH} caracteres`,
  )
}

// mirrors LoginRequestDTO
export const loginSchema = z.object({
  email: z.string().trim().min(1, "O e-mail é obrigatório").email("E-mail inválido"),
  password: passwordField("A senha é obrigatória"),
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
    password: newPasswordField("A senha é obrigatória"),
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
    newPassword: newPasswordField("A senha é obrigatória"),
    confirmPassword: z.string().min(1, "Confirme a nova senha"),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: "As senhas não coincidem",
    path: ["confirmPassword"],
  })
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>
