import { describe, expect, it } from "vitest"
import { createDailySchema } from "./daily"
import { playerStarsSchema } from "./pelada"
import { validateImageFile } from "./upload"
import { profileSchema } from "./user"

describe("createDailySchema (CreateDailyRequestDTO)", () => {
  it("requires a date and an HH:mm time", () => {
    expect(createDailySchema.safeParse({ dailyDate: null, dailyTime: "08:00" }).success).toBe(false)
    expect(createDailySchema.safeParse({ dailyDate: new Date(), dailyTime: "8h" }).success).toBe(false)
    expect(createDailySchema.safeParse({ dailyDate: new Date(), dailyTime: "08:00" }).success).toBe(true)
  })
})

describe("profileSchema (UpdateProfileRequest)", () => {
  it("mirrors the backend constraints", () => {
    expect(profileSchema.safeParse({ username: "ab", position: "" }).success).toBe(false)
    expect(profileSchema.safeParse({ username: "ana", position: "LIBERO" }).success).toBe(false)
    expect(profileSchema.safeParse({ username: "ana", position: "" }).success).toBe(true)
  })
})

describe("validateImageFile (FileUploadService)", () => {
  it("accepts small JPEG/PNG/WebP and rejects the rest", () => {
    expect(validateImageFile(new File(["x"], "a.png", { type: "image/png" }))).toBeNull()
    expect(validateImageFile(new File(["x"], "a.gif", { type: "image/gif" }))).toMatch(/JPEG/)
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], "a.jpg", { type: "image/jpeg" })
    expect(validateImageFile(big)).toMatch(/5 MB/)
  })
})

describe("playerStarsSchema (UpdatePlayerStarsRequestDTO)", () => {
  it("accepts 1 to 5 whole stars", () => {
    expect(playerStarsSchema.safeParse({ stars: 0 }).success).toBe(false)
    expect(playerStarsSchema.safeParse({ stars: 6 }).success).toBe(false)
    expect(playerStarsSchema.safeParse({ stars: 2.5 }).success).toBe(false)
    expect(playerStarsSchema.safeParse({ stars: 1 }).success).toBe(true)
    expect(playerStarsSchema.safeParse({ stars: 5 }).success).toBe(true)
  })
})
