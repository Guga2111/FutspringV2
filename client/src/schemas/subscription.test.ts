import { describe, expect, it } from "vitest"
import { pixClaimSchema } from "@/schemas/subscription"

describe("pixClaimSchema", () => {
  it("takes one of the plans", () => {
    expect(pixClaimSchema.parse({ plan: "MONTHLY" })).toEqual({ plan: "MONTHLY" })
    expect(pixClaimSchema.parse({ plan: "ANNUAL" })).toEqual({ plan: "ANNUAL" })
  })

  it("refuses anything else with a Portuguese message", () => {
    const result = pixClaimSchema.safeParse({ plan: "WEEKLY" })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0].message).toBe("Escolha o plano")
  })
})
