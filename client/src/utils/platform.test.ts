import { describe, expect, it } from "vitest"
import type { PlatformEvent } from "@/types/platform"
import { eventLine, formatBillingDate, formatIsoDate, paymentsSummary, revenueChartData } from "./platform"

const event: PlatformEvent = {
  id: 1,
  type: "PAYMENT_RECORDED",
  actor: { id: 1, username: "Leal", image: null, position: null, isDeleted: false },
  kindBefore: "PAID",
  kindAfter: "PAID",
  // end of the day in São Paulo (23:59:59 -03:00)
  validUntilBefore: "2026-12-08T02:59:59Z",
  validUntilAfter: "2027-12-08T02:59:59Z",
  note: null,
  createdAt: "2026-11-04T18:00:00Z",
}

describe("formatBillingDate", () => {
  it("reads the instant in São Paulo", () => {
    expect(formatBillingDate("2026-12-08T02:59:59Z")).toBe("07/12/2026")
    expect(formatBillingDate("2026-12-08T03:00:00Z")).toBe("08/12/2026")
    expect(formatIsoDate("2026-11-04")).toBe("04/11/2026")
  })
})

describe("eventLine", () => {
  it("names the actor, the event and the validity change", () => {
    expect(eventLine(event)).toBe("Leal · Pagamento registrado · validade 07/12/2026 → 07/12/2027")
  })

  it("reads a missing actor as the system and a null validity", () => {
    expect(eventLine({ ...event, type: "EXEMPTED", actor: null, validUntilAfter: null }))
      .toBe("Sistema · Isentada · sem validade")
    expect(eventLine({ ...event, type: "CREATED", validUntilBefore: null })).toBe("Leal · Assinatura criada · validade até 07/12/2027")
  })

  it("leaves the validity out when it didn't change", () => {
    expect(eventLine({ ...event, type: "FOUNDER_CHANGED", validUntilAfter: event.validUntilBefore }))
      .toBe("Leal · Fundadora alterada")
  })
})

describe("paymentsSummary", () => {
  it("counts the payments", () => {
    expect(paymentsSummary(2, "398.00")).toBe("R$ 398,00 em 2 pagamentos")
    expect(paymentsSummary(1, "1234.50")).toBe("R$ 1.234,50 em 1 pagamento")
  })
})

describe("revenueChartData", () => {
  it("labels each month and keeps the exact total", () => {
    expect(revenueChartData([{ month: "2026-11", total: "218.90" }, { month: "2027-01", total: "0.00" }])).toEqual([
      { label: "nov/26", value: 218.9, total: "218.90" },
      { label: "jan/27", value: 0, total: "0.00" },
    ])
  })
})
