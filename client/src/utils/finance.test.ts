import { describe, expect, it } from "vitest"
import type { FinanceCharge } from "@/types/finance"
import { filterFinanceCharges, financeQueueCounts, formatFinanceMoney, normalizeFinanceAmount, sumFinanceAmounts } from "@/utils/finance"

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
