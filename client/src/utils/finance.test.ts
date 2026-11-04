import { describe, expect, it } from "vitest"
import type { FinanceCharge } from "@/types/finance"
import { clampFinancePage, financeChargeStatus, filterFinanceCharges, financeOptionsSummary, planReceiveAll, financePageCount, financeQueueCounts, formatFinanceMoney, normalizeFinanceAmount, sumFinanceAmounts } from "@/utils/finance"

it("formats cents without floating point rounding", () => {
  expect(formatFinanceMoney("999999999999.99")).toBe("R$ 999.999.999.999,99")
  expect(formatFinanceMoney("-0.01")).toBe("−R$ 0,01")
  expect(formatFinanceMoney("0.00")).toBe("R$ 0,00")
})
describe("natural monetary input", () => {
  it.each([["45", "45.00"], ["45,5", "45.50"], ["45,50", "45.50"], ["45.50", "45.50"], ["1.234,56", "1234.56"], ["1.234", "1234.00"], ["R$ 1.234,56", "1234.56"], ["-5,5", "-5.50"], ["-0,00", "0.00"], ["00045,00", "45.00"]])("normalizes %s exactly", (input, output) => expect(normalizeFinanceAmount(input)).toBe(output))
  it.each(["", "45,123", "1,234.56", "NaN", "1e3", "0.001.00", "1000000000000,00", "--1,00", "R$", "1 234,00"])("rejects ambiguous/invalid input %s", value => expect(normalizeFinanceAmount(value)).toBeNull())
  it("sums exact cents at the precision boundary", () => {
    expect(sumFinanceAmounts(["0,10", "0,20", "-0,01"])).toBe("0.29")
    expect(sumFinanceAmounts(["999999999999.99", "0.01"])).toBe("1000000000000.00")
  })
})
function charge(id: number, name: string, overrides: Partial<FinanceCharge> = {}): FinanceCharge {
  return { id, personId: id, userId: id, name, components: [], adjustment: "0.00", amountDue: "45.00", received: "0.00", outstanding: "45.00", paymentStatus: "UNPAID", overdue: false, canceled: false, dueDate: null, notices: [], ...overrides }
}
describe("administrator payment queue", () => {
  const pending = charge(1, "Álvaro")
  const overdue = charge(2, "Rafael", { overdue: true })
  const review = charge(3, "Bruno", { notices: [{ id: 1, userId: 3, amount: "45.00", status: "PENDING", createdAt: "2026-11-01T12:00:00Z", resolvedAt: null, resolvedBy: null, reason: null, receiptExpiresAt: null, receiptAvailable: false }] })
  const paid = charge(4, "Carlos", { paymentStatus: "PAID", received: "45.00", outstanding: "0.00" })
  const canceled = charge(5, "Daniel", { canceled: true })
  const rows = [pending, paid, canceled, overdue, review]
  it("receives everyone who owes, leaving out paid, canceled and payments to review", () => {
    const partial = charge(6, "Eduardo", { paymentStatus: "PARTIAL", received: "20.00", outstanding: "25.00" })
    const plan = planReceiveAll([...rows, partial])
    expect(plan.charges.map(c => c.id)).toEqual([1, 2, 6])
    expect(plan.total).toBe("115.00")
    expect(plan.review).toBe(1)
  })
  it("prioritizes review and overdue obligations without mutating the server rows", () => {
    expect(filterFinanceCharges(rows, "pending", "").map(c => c.id)).toEqual([3, 2, 1])
    expect(rows.map(c => c.id)).toEqual([1, 4, 5, 2, 3])
  })
  it("keeps paid and canceled records accessible without polluting the pending queue", () => {
    expect(financeQueueCounts(rows)).toEqual({ pending: 3, review: 1, paid: 1, all: 5 })
    expect(filterFinanceCharges(rows, "review", "").map(c => c.id)).toEqual([3])
    expect(filterFinanceCharges(rows, "paid", "").map(c => c.id)).toEqual([4])
    expect(filterFinanceCharges(rows, "all", "").map(c => c.id)).toEqual([3, 2, 1, 4, 5])
  })
  it("matches names without case or accents within the chosen filter", () => {
    expect(filterFinanceCharges(rows, "pending", "alvARO").map(c => c.id)).toEqual([1])
    expect(filterFinanceCharges(rows, "review", "alvaro")).toEqual([])
  })
})

describe("finance pages", () => {
  it("counts at least one page", () => {
    expect(financePageCount(0, 10)).toBe(1)
    expect(financePageCount(10, 10)).toBe(1)
    expect(financePageCount(11, 10)).toBe(2)
  })
  it("moves back when the current page disappears", () => {
    expect(clampFinancePage(2, 21, 10)).toBe(2)
    expect(clampFinancePage(2, 20, 10)).toBe(1)
    expect(clampFinancePage(1, 0, 10)).toBe(0)
  })
  it("keeps the page when the API sends no total", () => {
    expect(clampFinancePage(0, undefined, 10)).toBe(0)
    expect(clampFinancePage(2, Number.NaN, 10)).toBe(2)
  })
})

describe("financeOptionsSummary", () => {
  const defaults = { pixKey: "pix@futspring.com", pixRecipient: "Leal" }
  const base = { componentCount: 1, pixKey: "pix@futspring.com", pixRecipient: "Leal" }

  it("says nothing was changed", () => {
    expect(financeOptionsSummary(base, defaults)).toBe("Sem vencimento · Pix padrão")
  })

  it("lists what is set", () => {
    expect(financeOptionsSummary({ ...base, dueDate: "2026-11-15", detailedPrice: true, componentCount: 3, dailyId: 4, description: " Trazer colete ", pixKey: "outra@pix.com" }, defaults))
      .toBe("Vence 15/11 · 3 itens · Vinculada a uma sessão · Com observações · Pix próprio")
  })

  it("has no Pix without a key", () => {
    expect(financeOptionsSummary({ componentCount: 1, pixKey: "", pixRecipient: "" }, undefined)).toBe("Sem vencimento · Sem Pix")
  })
})

describe("charge status", () => {
  it("cancellation wins over the payment status", () => {
    expect(financeChargeStatus({ canceled: true, paymentStatus: "PAID" })).toBe("CANCELED")
    expect(financeChargeStatus({ canceled: false, paymentStatus: "PARTIAL" })).toBe("PARTIAL")
    expect(financeChargeStatus({ canceled: false, paymentStatus: "UNPAID" })).toBe("UNPAID")
    expect(financeChargeStatus({ canceled: false, paymentStatus: "PAID" })).toBe("PAID")
  })
})
