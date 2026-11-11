import { describe, expect, it } from "vitest"
import { changePasswordSchema, deleteAccountSchema, profileSchema } from "./user"

describe("profileSchema (UpdateProfileRequest)", () => {
  it("takes 3–30 characters and an editable position or none", () => {
    expect(profileSchema.safeParse({ username: "Al", position: "" }).success).toBe(false)
    expect(profileSchema.safeParse({ username: "Leal", position: "MEIO" }).success).toBe(true)
    expect(profileSchema.safeParse({ username: "Leal", position: "GOALKEEPER" }).success).toBe(false)
  })
})

describe("changePasswordSchema (ChangePasswordRequestDTO)", () => {
  const valid = { currentPassword: "senha-atual", newPassword: "nova-senha", confirmPassword: "nova-senha" }

  it("accepts a filled current password and a confirmed new one of 8+ characters", () => {
    expect(changePasswordSchema.safeParse(valid).success).toBe(true)
  })

  it("requires the current password", () => {
    expect(changePasswordSchema.safeParse({ ...valid, currentPassword: "" }).success).toBe(false)
  })

  it("requires 8 characters", () => {
    expect(changePasswordSchema.safeParse({ ...valid, newPassword: "curta", confirmPassword: "curta" }).success).toBe(false)
  })

  it("puts a different confirmation on confirmPassword", () => {
    const result = changePasswordSchema.safeParse({ ...valid, confirmPassword: "outra-senha" })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0].path).toEqual(["confirmPassword"])
  })
})

describe("deleteAccountSchema (DeleteAccountRequestDTO)", () => {
  it("requires the password", () => {
    expect(deleteAccountSchema.safeParse({ password: "" }).success).toBe(false)
    expect(deleteAccountSchema.safeParse({ password: "x" }).success).toBe(true)
  })
})

describe("password limits (BcryptLength)", () => {
  it("rejects passwords above 72 bytes", () => {
    const accents = "é".repeat(40)
    expect(changePasswordSchema.safeParse({ currentPassword: "senha123", newPassword: accents, confirmPassword: accents }).success).toBe(false)
    expect(changePasswordSchema.safeParse({ currentPassword: "a".repeat(73), newPassword: "novasenha1", confirmPassword: "novasenha1" }).success).toBe(false)
    expect(deleteAccountSchema.safeParse({ password: "a".repeat(73) }).success).toBe(false)
    expect(deleteAccountSchema.safeParse({ password: "a".repeat(72) }).success).toBe(true)
  })
})
