import { describe, expect, it } from "vitest"
import { makeRecordPaymentSchema, subscriptionActionSchema, voidPaymentSchema } from "./platform"

const payment = makeRecordPaymentSchema("2026-11-04")
const valid = { plan: "ANNUAL", amount: "199,00", paidOn: "2026-11-04", note: "" }

describe("makeRecordPaymentSchema", () => {
  it("normalizes a Brazilian amount to the API's string", () => {
    expect(payment.parse(valid).amount).toBe("199.00")
    expect(payment.parse({ ...valid, amount: "19.9" }).amount).toBe("19.90")
    expect(payment.parse({ ...valid, amount: "1.234,56" }).amount).toBe("1234.56")
  })

  it("rejects zero, negatives, too many digits and text", () => {
    for (const amount of ["0", "0,00", "-5", "123456789,00", "abc", ""]) {
      expect(payment.safeParse({ ...valid, amount }).success, amount).toBe(false)
    }
    expect(payment.safeParse({ ...valid, amount: "12345678,00" }).success).toBe(true)
  })

  it("rejects a date in the future and a missing plan", () => {
    expect(payment.safeParse({ ...valid, paidOn: "2026-11-05" }).success).toBe(false)
    expect(payment.safeParse({ ...valid, paidOn: "" }).success).toBe(false)
    expect(payment.safeParse({ ...valid, plan: "" }).success).toBe(false)
  })

  it("caps the note at 500 characters", () => {
    expect(payment.safeParse({ ...valid, note: "a".repeat(501) }).success).toBe(false)
  })
})

describe("subscriptionActionSchema", () => {
  it("requires a note on every action", () => {
    for (const action of ["EXEMPT", "CANCEL"] as const) {
      expect(subscriptionActionSchema.safeParse({ action, note: "  " }).success).toBe(false)
      expect(subscriptionActionSchema.safeParse({ action, note: "Amigo" }).success).toBe(true)
    }
  })

  it("checks the field of each action", () => {
    expect(subscriptionActionSchema.safeParse({ action: "EXTEND_DAYS", days: 0, note: "x" }).success).toBe(false)
    expect(subscriptionActionSchema.safeParse({ action: "EXTEND_DAYS", days: 367, note: "x" }).success).toBe(false)
    expect(subscriptionActionSchema.safeParse({ action: "EXTEND_DAYS", days: 366, note: "x" }).success).toBe(true)
    expect(subscriptionActionSchema.safeParse({ action: "SET_VALID_UNTIL", validUntil: "", note: "x" }).success).toBe(false)
    expect(subscriptionActionSchema.safeParse({ action: "SET_VALID_UNTIL", validUntil: "2026-12-07", note: "x" }).success).toBe(true)
  })
})

describe("voidPaymentSchema", () => {
  it("requires a reason", () => {
    expect(voidPaymentSchema.safeParse({ reason: " " }).success).toBe(false)
    expect(voidPaymentSchema.safeParse({ reason: "Pix devolvido" }).success).toBe(true)
  })
})
