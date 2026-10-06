import { describe, expect, it } from "vitest"
import { forgotPasswordSchema, resetPasswordSchema } from "./auth"

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
