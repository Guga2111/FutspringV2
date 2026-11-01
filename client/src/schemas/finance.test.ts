import { describe, expect, it } from "vitest"
import { financeMoneySchema, financeSignedMoneySchema, financeListSchema, financeCashSchema, financeReplacementSchema, financeImageSchema } from "@/schemas/finance"

describe("finance contracts", () => {
  it("keeps exact decimal strings and rejects precision/coercion", () => {
    expect(financeMoneySchema.parse("999999999999.99")).toBe("999999999999.99")
    for (const value of [45, 45.12, "45", "45.0", "45.001", "-1.00", "NaN", "1e2", "1000000000000.00"]) expect(financeMoneySchema.safeParse(value).success).toBe(false)
    expect(financeSignedMoneySchema.parse("-5.00")).toBe("-5.00")
  })
  it("models configurable collections without formulas", () => {
    const list = { name: "Mensalidade de novembro", classification: "MONTHLY", description: "Quatro diárias", dueDate: "2026-11-05", dailyId: null, components: [{ name: "Campo", amount: "160.00" }, { name: "Juiz", amount: "20.00" }], pixKey: "pix", pixRecipient: "Gestor", qrId: null, reason: "" }
    expect(financeListSchema.safeParse(list).success).toBe(true)
    expect(financeListSchema.safeParse({ ...list, components: [] }).success).toBe(false)
    expect(financeListSchema.safeParse({ ...list, components: [{ name: "Campo", amount: "=40*4" }] }).success).toBe(false)
  })
  it("requires realized expenses and audited opening balances", () => {
    const base = { kind: "EXPENSE", amount: "20.00", description: "Juiz", category: "Arbitragem", effectiveDate: "", method: "CASH", listId: null, dailyId: null, reason: "" }
    expect(financeCashSchema.safeParse(base).success).toBe(true)
    expect(financeCashSchema.safeParse({ ...base, category: "" }).success).toBe(false)
    expect(financeCashSchema.safeParse({ ...base, amount: "-1.00" }).success).toBe(false)
    expect(financeCashSchema.safeParse({ ...base, kind: "OPENING", amount: "-20.00", reason: "Reconciliação inicial" }).success).toBe(true)
  })
  it("requires exactly one replacement identity", () => {
    expect(financeReplacementSchema.safeParse({ userId: 1, guestName: "", reason: "Troca" }).success).toBe(true)
    expect(financeReplacementSchema.safeParse({ userId: null, guestName: "Convidado", reason: "Troca" }).success).toBe(true)
    expect(financeReplacementSchema.safeParse({ userId: 1, guestName: "Convidado", reason: "Troca" }).success).toBe(false)
    expect(financeReplacementSchema.safeParse({ userId: null, guestName: "", reason: "Troca" }).success).toBe(false)
  })
  it("restricts financial images to JPEG/PNG and 5 MiB", () => {
    expect(financeImageSchema.safeParse(new File(["png"], "a.png", { type: "image/png" })).success).toBe(true)
    expect(financeImageSchema.safeParse(new File(["webp"], "a.webp", { type: "image/webp" })).success).toBe(false)
    expect(financeImageSchema.safeParse(new File([new Uint8Array(5 * 1024 * 1024 + 1)], "a.png", { type: "image/png" })).success).toBe(false)
  })
})

describe("daily organizer forms", () => {
  it("normalizes natural Brazilian input while keeping API schemas strict", async () => {
    const { financeAmountInputSchema, financeSignedAmountInputSchema, financeReceiveFormSchema } = await import("@/schemas/finance")
    expect(financeAmountInputSchema.parse("1.234,56")).toBe("1234.56")
    expect(financeSignedAmountInputSchema.parse("-5,00")).toBe("-5.00")
    expect(financeAmountInputSchema.safeParse("-5,00").success).toBe(false)
    const payment = { action: "receive", amount: "25,50", effectiveDate: "", method: "PIX", reason: "", reduceObligation: false, file: null }
    expect(financeReceiveFormSchema.parse(payment).amount).toBe("25.50")
    expect(financeReceiveFormSchema.safeParse({ ...payment, amount: "0" }).success).toBe(false)
    expect(financeMoneySchema.safeParse("25,50").success).toBe(false)
  })
  it("uses the detailed price only when the organizer chooses it", async () => {
    const { financeCreateSchema } = await import("@/schemas/finance")
    const values = { name: "Diária de sábado", classification: "DAILY", amount: "45", detailedPrice: false, description: "", dueDate: "", dailyId: null, components: [{ name: "Campo", amount: "40,00" }, { name: "Juiz", amount: "5,50" }], pixKey: "", pixRecipient: "", qrId: null, reason: "", userIds: [1], guestNames: ["Convidado", ""] }
    expect(financeCreateSchema.parse(values).amount).toBe("45.00")
    expect(financeCreateSchema.parse({ ...values, detailedPrice: true }).amount).toBe("45.50")
    expect(financeCreateSchema.parse({ ...values, components: [{ name: "", amount: "inválido" }] }).components).toEqual([{ name: "Valor por pessoa", amount: "45.00" }])
    expect(financeCreateSchema.safeParse({ ...values, detailedPrice: true, components: [{ name: "", amount: "inválido" }] }).success).toBe(false)
    expect(financeCreateSchema.parse(values).guestNames).toEqual(["Convidado"])
    expect(financeCreateSchema.safeParse({ ...values, amount: "0" }).success).toBe(false)
  })
})
