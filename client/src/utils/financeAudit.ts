import type { FinanceAudit, FinanceCharge, FinanceDetail, FinanceMovement, FinanceSettings, FinanceTemplate } from "@/types/finance"
import { financeChargeStatusLabel, financeListStatusLabel, financeMovementLabel, financeVisibilityLabel } from "@/types/finance"
import { financeChargeStatus, formatFinanceMoney, sumFinanceAmounts } from "@/utils/finance"
import { formatShortDate } from "@/utils/dates"

// One readable line of an audit entry: "Vencimento: 10/11 → 15/11", "Ana: Em aberto → Paga", "+ Pedro"
export interface FinanceAuditChange {
  label: string
  before?: string
  after?: string
}

type Entry = Pick<FinanceAudit, "operation" | "target" | "beforeData" | "afterData">
type Person = { id: number; username: string }

// The backend stores the JSON of the views it returned (FinanceTransactions): a list Detail for list, charge and
// payment operations, Settings, a Template, a Movement, the manager ids or a QR id. Anything else reads as nothing.
function parse(json: string | null): unknown {
  if (json === null) return null
  try {
    return JSON.parse(json)
  } catch {
    return null
  }
}

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === "object" && value !== null && !Array.isArray(value)
const isDetail = (value: unknown): value is FinanceDetail =>
  isRecord(value) && isRecord(value.collection) && typeof value.collection.name === "string" && Array.isArray(value.charges)
const isSettings = (value: unknown): value is FinanceSettings => isRecord(value) && typeof value.visibility === "string"
const isTemplate = (value: unknown): value is FinanceTemplate =>
  isRecord(value) && typeof value.name === "string" && typeof value.classification === "string" && Array.isArray(value.components)
const isMovement = (value: unknown): value is FinanceMovement =>
  isRecord(value) && typeof value.kind === "string" && typeof value.amount === "string" && typeof value.description === "string"
const isIds = (value: unknown): value is number[] => Array.isArray(value) && value.every((id) => typeof id === "number")

// CHARGE:{list}:{charge} or CHARGE:{charge}; NOTICE:{list}:{charge}:{notice}
function targetChargeId(target: string): number | null {
  const parts = target.split(":")
  const id = parts[0] === "CHARGE" ? parts[parts.length - 1] : parts[0] === "NOTICE" ? parts[2] : null
  return id && /^\d+$/.test(id) ? Number(id) : null
}

const money = (value: string | null | undefined) => (value ? formatFinanceMoney(value) : null)
const date = (value: string | null | undefined) => (value ? formatShortDate(value) : null)
const perPerson = (components: { amount: string }[]) => formatFinanceMoney(sumFinanceAmounts(components.map((c) => c.amount)))

// "Ana · Mensalidade de novembro", "Mensalidade de novembro", "Modelo Diária", "Pix", or null when nothing names it
export function auditSubject(entry: Entry): string | null {
  const after = parse(entry.afterData)
  const before = parse(entry.beforeData)
  const detail = isDetail(after) ? after : isDetail(before) ? before : null
  if (detail) {
    const chargeId = targetChargeId(entry.target)
    const charge = chargeId === null ? undefined : [...(isDetail(after) ? after.charges : []), ...(isDetail(before) ? before.charges : [])].find((c) => c.id === chargeId)
    return charge ? `${charge.name} · ${detail.collection.name}` : detail.collection.name
  }
  switch (entry.operation) {
    case "TEMPLATE": {
      const template = isTemplate(after) ? after : isTemplate(before) ? before : null
      return template ? `Modelo ${template.name}` : null
    }
    case "SETTINGS":
      return "Pix e visibilidade"
    case "QR":
      return entry.target === "QR:null" ? "Pix" : "QR Code da cobrança"
    case "MANAGERS":
      return "Responsáveis financeiros"
    case "CASH":
      return isMovement(after) ? after.description : null
    case "REVERSE":
      return isMovement(before) ? before.description : null
    default:
      return null
  }
}

// A field that changed; with no previous state (creation) only the new value is listed
function field(changes: FinanceAuditChange[], label: string, before: string | null | undefined, after: string | null | undefined, created: boolean) {
  if (created) {
    if (after) changes.push({ label, after })
  } else if ((before ?? null) !== (after ?? null)) {
    changes.push({ label, before: before ?? "—", after: after ?? "—" })
  }
}

function detailChanges(before: FinanceDetail | null, after: FinanceDetail): FinanceAuditChange[] {
  const changes: FinanceAuditChange[] = []
  const created = before === null
  const a = after.collection
  const b = before?.collection
  field(changes, "Nome", b?.name, a.name, created)
  field(changes, "Vencimento", date(b?.dueDate), date(a.dueDate), created)
  field(changes, "Valor por pessoa", b && perPerson(b.components ?? []), perPerson(a.components ?? []), created)
  if (!created) field(changes, "Situação", b && financeListStatusLabel[b.status], financeListStatusLabel[a.status], false)
  field(changes, "Chave Pix", b?.pixKey, a.pixKey, created)
  field(changes, "Quem recebe", b?.pixRecipient, a.pixRecipient, created)
  field(changes, "Observações", b?.description, a.description, created)
  if (!created && b?.qrId !== a.qrId) changes.push({ label: "QR Code trocado" })
  const previous = new Map((before?.charges ?? []).map((c): [number, FinanceCharge] => [c.id, c]))
  for (const charge of after.charges) {
    const old = previous.get(charge.id)
    if (!old) {
      changes.push({ label: `+ ${charge.name}` })
      continue
    }
    if (charge.canceled && !old.canceled) {
      changes.push({ label: `− ${charge.name} (cancelada)` })
      continue
    }
    const status = [old, charge].map((c) => financeChargeStatusLabel[financeChargeStatus(c)])
    if (status[0] !== status[1]) changes.push({ label: charge.name, before: status[0], after: status[1] })
    field(changes, `${charge.name} · valor`, money(old.amountDue), money(charge.amountDue), false)
    field(changes, `${charge.name} · vencimento`, date(old.dueDate), date(charge.dueDate), false)
  }
  return changes
}

function settingsChanges(before: FinanceSettings, after: FinanceSettings): FinanceAuditChange[] {
  const changes: FinanceAuditChange[] = []
  field(changes, "Visibilidade", financeVisibilityLabel[before.visibility], financeVisibilityLabel[after.visibility], false)
  field(changes, "Chave Pix", before.pixKey, after.pixKey, false)
  field(changes, "Quem recebe", before.pixRecipient, after.pixRecipient, false)
  if (before.qrId !== after.qrId) changes.push({ label: after.qrId === null ? "QR Code removido" : "QR Code trocado" })
  return changes
}

function templateChanges(before: FinanceTemplate | null, after: FinanceTemplate): FinanceAuditChange[] {
  const changes: FinanceAuditChange[] = []
  const created = before === null
  field(changes, "Nome", before?.name, after.name, created)
  field(changes, "Valor por pessoa", before && perPerson(before.components), perPerson(after.components), created)
  field(changes, "Descrição", before?.description, after.description, created)
  if (before && before.archived !== after.archived) changes.push({ label: after.archived ? "Modelo arquivado" : "Modelo reativado" })
  return changes
}

function movementChanges(movement: FinanceMovement): FinanceAuditChange[] {
  return [
    { label: "Tipo", after: financeMovementLabel[movement.kind] },
    { label: "Valor", after: formatFinanceMoney(movement.amount) },
    { label: "Data", after: formatShortDate(movement.effectiveDate) },
  ]
}

// The readable lines of what an audit entry changed; [] when its data is missing or has an unknown shape
export function auditChanges(entry: Entry, members: Person[]): FinanceAuditChange[] {
  const before = parse(entry.beforeData)
  const after = parse(entry.afterData)
  if (isDetail(after)) return detailChanges(isDetail(before) ? before : null, after)
  switch (entry.operation) {
    case "SETTINGS":
      return isSettings(before) && isSettings(after) ? settingsChanges(before, after) : []
    case "MANAGERS": {
      if (!isIds(before) || !isIds(after)) return []
      const name = (id: number) => members.find((m) => m.id === id)?.username ?? `Membro #${id}`
      return [
        ...after.filter((id) => !before.includes(id)).map((id) => ({ label: `+ ${name(id)}` })),
        ...before.filter((id) => !after.includes(id)).map((id) => ({ label: `− ${name(id)}` })),
      ]
    }
    case "TEMPLATE":
      return isTemplate(after) ? templateChanges(isTemplate(before) ? before : null, after) : []
    case "CASH":
      return isMovement(after) ? movementChanges(after) : []
    case "REVERSE":
      return isMovement(before) ? [{ label: "Lançamento anulado", after: `${before.description} · ${formatFinanceMoney(before.amount)}` }] : []
    case "QR":
      return typeof after === "number" && before !== after ? [{ label: "QR Code trocado" }] : []
    default:
      return []
  }
}
