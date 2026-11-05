import { describe, expect, it } from "vitest"
import type { FinanceCharge, FinanceComponent, FinanceDetail, FinanceList, FinanceNotice } from "@/types/finance"
import { financeWhatsAppMessage, whatsAppUrl } from "@/utils/financeWhatsApp"

const LINK = "https://futspring.test/pelada/7/financeiro/3"
const TODAY = "2026-11-05"
const COMPONENTS: FinanceComponent[] = [{ name: "Campo", amount: "40.00" }, { name: "Bola", amount: "5.50" }]

function list(overrides: Partial<FinanceList> = {}): FinanceList {
  return {
    id: 3, name: "Mensalidade de novembro", classification: "MONTHLY", description: null, components: COMPONENTS,
    pixKey: "pix@futspring.com", pixRecipient: "Leal", qrId: null, status: "OPEN", dueDate: "2026-11-15", dailyId: null,
    dailyLabel: null, publishedAt: "2026-11-01T12:00:00Z", totals: { due: "0.00", received: "0.00", outstanding: "0.00", pendingNotices: 0 },
    ...overrides,
  }
}

function charge(id: number, name: string, overrides: Partial<FinanceCharge> = {}): FinanceCharge {
  return {
    id, personId: id, userId: id, name, components: COMPONENTS, adjustment: "0.00", amountDue: "45.50", received: "0.00",
    outstanding: "45.50", paymentStatus: "UNPAID", overdue: false, canceled: false, dueDate: null, notices: [], ...overrides,
  }
}

const pendingNotice: FinanceNotice = { id: 9, userId: 0, amount: "45.50", status: "PENDING", createdAt: "2026-11-04T12:00:00Z", resolvedAt: null, resolvedBy: null, reason: null, receiptExpiresAt: null, receiptAvailable: false }
const paid = (id: number, name: string) => charge(id, name, { paymentStatus: "PAID", received: "45.50", outstanding: "0.00" })
const detail = (charges: FinanceCharge[], overrides: Partial<FinanceList> = {}): FinanceDetail => ({ collection: list(overrides), charges })

describe("financeWhatsAppMessage", () => {
  const charges = [
    charge(1, "Ítalo", { overdue: true }),
    charge(2, "bruno", { paymentStatus: "PARTIAL", received: "20.00", outstanding: "25.50" }),
    charge(3, "Ana"),
    paid(4, "Zeca"),
    paid(5, "Édson"),
    charge(6, "Carla", { notices: [pendingNotice] }),
    charge(7, "Daniel", { canceled: true }),
    charge(8, "Convidado Rui", { userId: null }),
  ]

  it("names who owes, who is under review and who paid, sorted without accents or case", () => {
    expect(financeWhatsAppMessage(detail(charges), LINK, TODAY)).toBe([
      "*Mensalidade de novembro*",
      "Vence 15/11",
      "",
      "• Campo — R$ 40,00",
      "• Bola — R$ 5,50",
      "*Total: R$ 45,50*",
      "",
      "⏳ Faltam pagar (4):",
      "1. Ana — R$ 45,50",
      "2. bruno — R$ 25,50 (parcial)",
      "3. Convidado Rui — R$ 45,50",
      "4. Ítalo — R$ 45,50 ⚠️ vencida",
      "🔎 Conferindo (1): Carla",
      "✅ Pagos (2):",
      "1. Édson — R$ 45,50",
      "2. Zeca — R$ 45,50",
      "",
      "Pix: pix@futspring.com (Leal)",
      "",
      `👉 Pague e informe aqui: ${LINK}`,
    ].join("\n"))
  })

  it("says everybody paid when nobody owes and nothing is under review", () => {
    const text = financeWhatsAppMessage(detail([paid(1, "Ana"), paid(2, "Bia"), charge(3, "Caio", { canceled: true })]), LINK, TODAY)
    expect(text).toContain("✅ Todos pagaram!")
    expect(text).not.toContain("Pagos")
    expect(text).not.toContain("Faltam")
  })

  it("shows a single component as Valor and omits empty blocks", () => {
    const one = [{ name: "Diária", amount: "20.00" }]
    const text = financeWhatsAppMessage(detail([charge(1, "Ana", { components: one })], { components: one }), LINK, TODAY)
    expect(text).toContain("Valor: R$ 20,00")
    expect(text).not.toContain("Total")
    expect(text).not.toContain("Pagos")
    expect(text).not.toContain("Alguns participantes")
  })

  it("notes different values from an adjustment or other components, ignoring canceled charges", () => {
    const adjusted = financeWhatsAppMessage(detail([charge(1, "Ana", { adjustment: "-5.00" })]), LINK, TODAY)
    expect(adjusted).toContain("Alguns participantes têm valor diferente.")
    const other = financeWhatsAppMessage(detail([charge(1, "Ana", { components: [{ name: "Campo", amount: "40.00" }] })]), LINK, TODAY)
    expect(other).toContain("Alguns participantes têm valor diferente.")
    const canceled = financeWhatsAppMessage(detail([charge(1, "Ana"), charge(2, "Bia", { canceled: true, adjustment: "3.00" })]), LINK, TODAY)
    expect(canceled).not.toContain("Alguns participantes")
  })

  it("reads due dates against today and drops the Pix line without a key", () => {
    expect(financeWhatsAppMessage(detail([charge(1, "Ana")], { dueDate: "2026-11-04" }), LINK, TODAY)).toContain("Venceu 04/11")
    expect(financeWhatsAppMessage(detail([charge(1, "Ana")], { dueDate: TODAY }), LINK, TODAY)).toContain("Vence 05/11")
    expect(financeWhatsAppMessage(detail([charge(1, "Ana")], { dueDate: "2027-01-10" }), LINK, TODAY)).toContain("Vence 10/01/2027")
    const plain = financeWhatsAppMessage(detail([charge(1, "Ana")], { dueDate: null, pixKey: null }), LINK, TODAY)
    expect(plain).not.toContain("Vence")
    expect(plain).not.toContain("Pix:")
    expect(financeWhatsAppMessage(detail([charge(1, "Ana")], { pixRecipient: null }), LINK, TODAY)).toContain("Pix: pix@futspring.com\n")
  })
})

describe("whatsAppUrl", () => {
  it("encodes the text into the wa.me link", () => {
    expect(whatsAppUrl("*Olá* & tchau")).toBe("https://wa.me/?text=*Ol%C3%A1*%20%26%20tchau")
  })
})
