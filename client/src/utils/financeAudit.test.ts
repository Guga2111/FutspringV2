import { describe, expect, it } from "vitest"
import type { FinanceAudit, FinanceCharge, FinanceDetail, FinanceMovement, FinanceSettings, FinanceTemplate } from "@/types/finance"
import { auditChanges, auditSubject } from "@/utils/financeAudit"
import { formatShortDate } from "@/utils/dates"

const members = [{ id: 1, username: "Ana" }, { id: 2, username: "Pedro" }]

function charge(id: number, name: string, patch: Partial<FinanceCharge> = {}): FinanceCharge {
  return {
    id, personId: id, userId: id, name, components: [{ name: "Campo", amount: "40.00" }], adjustment: "0.00", amountDue: "40.00",
    received: "0.00", outstanding: "40.00", paymentStatus: "UNPAID", overdue: false, canceled: false, dueDate: "2026-11-10", notices: [], ...patch,
  }
}
function detail(charges: FinanceCharge[], patch: Partial<FinanceDetail["collection"]> = {}): FinanceDetail {
  return {
    collection: {
      id: 12, name: "Mensalidade de novembro", classification: "MONTHLY", status: "OPEN", description: null, dueDate: "2026-11-10",
      dailyId: null, dailyLabel: null, pixKey: "pix@x.com", pixRecipient: "Leal", qrId: null, components: [{ name: "Campo", amount: "40.00" }],
      publishedAt: "2026-11-01T12:00:00Z", totals: { due: "0.00", received: "0.00", outstanding: "0.00", pendingNotices: 0 }, ...patch,
    },
    charges,
  }
}
const entry = (operation: FinanceAudit["operation"], target: string, before: unknown, after: unknown) => ({
  operation, target, beforeData: before === undefined ? null : JSON.stringify(before), afterData: after === undefined ? null : JSON.stringify(after),
})
const movement = (patch: Partial<FinanceMovement> = {}): FinanceMovement => ({
  id: 5, listId: null, dailyId: null, dailyLabel: null, chargeId: null, noticeId: null, originalId: null, kind: "EXPENSE", amount: "-350.00",
  obligationDelta: "0.00", effectiveDate: "2026-11-02", method: "PIX", description: "Aluguel do campo", category: "Campo", createdBy: 1,
  createdAt: "2026-11-02T12:00:00Z", reversed: false, ...patch,
})

describe("auditSubject", () => {
  it("names the person and the collection on charge and notice targets", () => {
    const d = detail([charge(34, "Ana")])
    expect(auditSubject(entry("RECEIVE", "CHARGE:12:34", d, d))).toBe("Ana · Mensalidade de novembro")
    expect(auditSubject(entry("CHARGE_CANCEL", "CHARGE:34", d, d))).toBe("Ana · Mensalidade de novembro")
    expect(auditSubject(entry("CONFIRM", "NOTICE:12:34:9", d, d))).toBe("Ana · Mensalidade de novembro")
    expect(auditSubject(entry("PUBLISH", "LIST:12", d, d))).toBe("Mensalidade de novembro")
  })
  it("names templates, Pix, managers and movements", () => {
    const template: FinanceTemplate = { id: 3, name: "Diária", classification: "DAILY", description: null, components: [], pixKey: null, pixRecipient: null, qrId: null, archived: false }
    expect(auditSubject(entry("TEMPLATE", "TEMPLATE:3", undefined, template))).toBe("Modelo Diária")
    expect(auditSubject(entry("QR", "QR:null", 1, 2))).toBe("Pix")
    expect(auditSubject(entry("MANAGERS", "MANAGERS", [], [1]))).toBe("Responsáveis financeiros")
    expect(auditSubject(entry("CASH", "CASH", undefined, movement()))).toBe("Aluguel do campo")
    expect(auditSubject(entry("REVERSE", "MOVEMENT:5", movement(), movement({ kind: "REVERSAL", description: "Anulação" })))).toBe("Aluguel do campo")
    expect(auditSubject(entry("DAILY_DETACHED", "DAILY_DETACHED", undefined, undefined))).toBeNull()
  })
})

describe("auditChanges", () => {
  it("lists a new collection with its people", () => {
    const changes = auditChanges(entry("LIST_SAVE", "LIST:12", undefined, detail([charge(1, "Ana"), charge(2, "Pedro")])), members)
    expect(changes).toContainEqual({ label: "Valor por pessoa", after: "R$ 40,00" })
    expect(changes).toContainEqual({ label: "Vencimento", after: formatShortDate("2026-11-10") })
    expect(changes).toContainEqual({ label: "+ Ana" })
    expect(changes).toContainEqual({ label: "+ Pedro" })
  })
  it("shows edited collection fields before and after", () => {
    const before = detail([], { dueDate: "2026-11-10", status: "DRAFT" })
    const after = detail([], { dueDate: "2026-11-15", status: "OPEN", description: "Trazer colete" })
    expect(auditChanges(entry("PUBLISH", "LIST:12", before, after), members)).toEqual([
      { label: "Vencimento", before: formatShortDate("2026-11-10"), after: formatShortDate("2026-11-15") },
      { label: "Situação", before: "Rascunho", after: "Aberta" },
      { label: "Observações", before: "—", after: "Trazer colete" },
    ])
  })
  it("shows added participants, payments, value changes and cancellations", () => {
    const before = detail([charge(1, "Ana"), charge(2, "Pedro")])
    expect(auditChanges(entry("PARTICIPANTS", "LIST:12", before, detail([...before.charges, charge(3, "Caio")])), members)).toEqual([{ label: "+ Caio" }])
    const paid = detail([charge(1, "Ana", { paymentStatus: "PAID", received: "40.00", outstanding: "0.00" }), charge(2, "Pedro")])
    expect(auditChanges(entry("RECEIVE", "CHARGE:12:1", before, paid), members)).toEqual([{ label: "Ana", before: "Em aberto", after: "Paga" }])
    const raised = detail([charge(1, "Ana", { amountDue: "45.00" }), charge(2, "Pedro")])
    expect(auditChanges(entry("CHARGE_SAVE", "CHARGE:12:1", before, raised), members)).toEqual([{ label: "Ana · valor", before: "R$ 40,00", after: "R$ 45,00" }])
    const canceled = detail([charge(1, "Ana", { canceled: true }), charge(2, "Pedro")])
    expect(auditChanges(entry("CHARGE_CANCEL", "CHARGE:1", before, canceled), members)).toEqual([{ label: "− Ana (cancelada)" }])
  })
  it("reads settings, managers, templates, cash, reversals and QR", () => {
    const settings: FinanceSettings = { visibility: "INDIVIDUAL", pixKey: "a@x.com", pixRecipient: "Leal", qrId: 1, managerIds: [] }
    expect(auditChanges(entry("SETTINGS", "SETTINGS", settings, { ...settings, visibility: "TRANSPARENT", qrId: 2 }), members)).toEqual([
      { label: "Visibilidade", before: "Individual", after: "Transparente" },
      { label: "QR Code trocado" },
    ])
    expect(auditChanges(entry("MANAGERS", "MANAGERS", [1], [2, 9]), members)).toEqual([{ label: "+ Pedro" }, { label: "+ Membro #9" }, { label: "− Ana" }])
    const template: FinanceTemplate = { id: 3, name: "Diária", classification: "DAILY", description: null, components: [{ name: "Campo", amount: "20.00" }], pixKey: null, pixRecipient: null, qrId: null, archived: false }
    expect(auditChanges(entry("TEMPLATE", "TEMPLATE:3", template, { ...template, components: [{ name: "Campo", amount: "25.00" }] }), members)).toEqual([
      { label: "Valor por pessoa", before: "R$ 20,00", after: "R$ 25,00" },
    ])
    expect(auditChanges(entry("CASH", "CASH", undefined, movement()), members)).toEqual([
      { label: "Tipo", after: "Despesa" },
      { label: "Valor", after: "−R$ 350,00" },
      { label: "Data", after: formatShortDate("2026-11-02") },
    ])
    expect(auditChanges(entry("REVERSE", "MOVEMENT:5", movement(), movement({ kind: "REVERSAL" })), members)).toEqual([
      { label: "Lançamento anulado", after: "Aluguel do campo · −R$ 350,00" },
    ])
    expect(auditChanges(entry("QR", "QR:null", 1, 2), members)).toEqual([{ label: "QR Code trocado" }])
  })
  it("returns nothing for missing data, invalid JSON or an unknown shape", () => {
    expect(auditChanges(entry("MANAGERS_REVOKED", "MANAGERS_REVOKED", undefined, undefined), members)).toEqual([])
    expect(auditChanges({ operation: "SETTINGS", target: "SETTINGS", beforeData: "{nope", afterData: "]" }, members)).toEqual([])
    expect(auditChanges(entry("SETTINGS", "SETTINGS", { foo: 1 }, { bar: 2 }), members)).toEqual([])
    expect(auditSubject({ operation: "LIST_SAVE", target: "LIST:1", beforeData: null, afterData: "not json" })).toBeNull()
  })
})
