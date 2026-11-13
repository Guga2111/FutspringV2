import { describe, expect, it } from "vitest"
import { LEGAL_EFFECTIVE_DATE, LEGAL_VERSION } from "@/lib/legal"

describe("legal version", () => {
  it("is an ISO date shown as dd/MM/yyyy", () => {
    expect(LEGAL_VERSION).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(LEGAL_EFFECTIVE_DATE).toBe("13/11/2026")
  })
})
