import { describe, expect, it } from "vitest"
import { forgotPasswordSchema, loginSchema, registerSchema, resetPasswordSchema } from "./auth"

describe("forgotPasswordSchema (ForgotPasswordRequestDTO)", () => {
  it("requires a valid e-mail and trims it", () => {
    expect(forgotPasswordSchema.safeParse({ email: "" }).success).toBe(false)
    expect(forgotPasswordSchema.safeParse({ email: "ana" }).success).toBe(false)
    expect(forgotPasswordSchema.parse({ email: " ana@example.com " }).email).toBe("ana@example.com")
  })
})

describe("resetPasswordSchema (ResetPasswordRequestDTO)", () => {
  it("requires 8 characters and a matching confirmation", () => {
    expect(resetPasswordSchema.safeParse({ newPassword: "curta", confirmPassword: "curta" }).success).toBe(false)
    expect(resetPasswordSchema.safeParse({ newPassword: "novasenha1", confirmPassword: "outrasenha" }).success).toBe(false)
    expect(resetPasswordSchema.safeParse({ newPassword: "novasenha1", confirmPassword: "novasenha1" }).success).toBe(true)
  })

  it("puts the mismatch error on confirmPassword", () => {
    const result = resetPasswordSchema.safeParse({ newPassword: "novasenha1", confirmPassword: "outrasenha" })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0].path).toEqual(["confirmPassword"])
  })
})

describe("loginSchema (LoginRequestDTO)", () => {
  it("requires a valid e-mail and a password", () => {
    expect(loginSchema.safeParse({ email: " leal@futspring.com ", password: "x" }).data?.email).toBe("leal@futspring.com")
    expect(loginSchema.safeParse({ email: "leal", password: "x" }).success).toBe(false)
    expect(loginSchema.safeParse({ email: "leal@futspring.com", password: "" }).success).toBe(false)
  })
})

describe("registerSchema (RegisterRequestDTO)", () => {
  const valid = { username: "Leal", email: "leal@futspring.com", password: "senha123", confirmPassword: "senha123", terms: true }
  it("accepts a complete registration", () => {
    expect(registerSchema.safeParse(valid).success).toBe(true)
  })
  it("mirrors the DTO limits", () => {
    expect(registerSchema.safeParse({ ...valid, username: "Le" }).success).toBe(false)
    expect(registerSchema.safeParse({ ...valid, username: "x".repeat(31) }).success).toBe(false)
    expect(registerSchema.safeParse({ ...valid, password: "1234567", confirmPassword: "1234567" }).success).toBe(false)
  })
  it("puts a mismatch on the confirmation and needs the terms", () => {
    const mismatch = registerSchema.safeParse({ ...valid, confirmPassword: "outra123" })
    expect(mismatch.error?.issues[0].path).toEqual(["confirmPassword"])
    expect(registerSchema.safeParse({ ...valid, terms: false }).success).toBe(false)
  })
})
