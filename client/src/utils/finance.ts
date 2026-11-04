import { normalizeSearch } from "@/lib/utils"
import type { FinanceCharge, FinanceChargeStatus, FinanceComponent } from "@/types/finance"

// Exact decimal strings throughout: formatting and parsing never use floating point arithmetic.
export function formatFinanceMoney(value: string) {
  const negative = value.startsWith("-")
  const [whole, cents] = (negative ? value.slice(1) : value).split(".")
  return `${negative ? "−" : ""}R$ ${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ".")},${cents}`
}

export function normalizeFinanceAmount(input: string): string | null {
  const value = input.trim().replace(/^R\$\s*/, "")
  let normalized: string
  if (/^-?\d{1,12}(?:\.\d{1,2})?$/.test(value)) {
    const [whole, cents = ""] = value.split(".")
    normalized = `${whole}.${cents.padEnd(2, "0")}`
  } else if (/^-?(?:\d{1,12}|\d{1,3}(?:\.\d{3})+)(?:,\d{1,2})?$/.test(value)) {
    const [whole, cents = ""] = value.split(",")
    normalized = `${whole.replaceAll(".", "")}.${cents.padEnd(2, "0")}`
  } else return null
  const [whole, cents] = normalized.split(".")
  const negative = whole.startsWith("-")
  const digits = whole.replace("-", "").replace(/^0+(?=\d)/, "")
  if (digits.length > 12) return null
  return `${negative && (digits !== "0" || cents !== "00") ? "-" : ""}${digits}.${cents}`
}

export function sumFinanceAmounts(values: string[]): string {
  const total = values.reduce((sum, value) => {
    const amount = normalizeFinanceAmount(value)
    if (amount === null) return sum
    const negative = amount.startsWith("-")
    const cents = BigInt(amount.replace("-", "").replace(".", ""))
    return sum + (negative ? -cents : cents)
  }, 0n)
  const absolute = total < 0n ? -total : total
  return `${total < 0n ? "-" : ""}${absolute / 100n}.${String(absolute % 100n).padStart(2, "0")}`
}

export const financeOptions = (map: Record<string, string>) => Object.entries(map).map(([value, label]) => ({ value, label }))
export type FinanceQueueFilter = "pending" | "review" | "paid" | "all"
export const financeQueueLabels: Record<FinanceQueueFilter, string> = { pending: "A receber", review: "Conferir", paid: "Pagos", all: "Todos" }
export function financeChargeStatus(charge: Pick<FinanceCharge, "canceled" | "paymentStatus">): FinanceChargeStatus {
  return charge.canceled ? "CANCELED" : charge.paymentStatus
}
export function pendingFinanceNotice(charge: FinanceCharge) { return charge.notices.find(n => n.status === "PENDING") }
export function financeQueueCounts(charges: FinanceCharge[]) {
  return {
    pending: charges.filter(c => !c.canceled && c.paymentStatus !== "PAID").length,
    review: charges.filter(c => !c.canceled && pendingFinanceNotice(c)).length,
    paid: charges.filter(c => !c.canceled && c.paymentStatus === "PAID").length,
    all: charges.length,
  }
}
export function filterFinanceCharges(charges: FinanceCharge[], filter: FinanceQueueFilter, search: string) {
  const query = normalizeSearch(search)
  const priority = (c: FinanceCharge) => c.canceled ? 4 : pendingFinanceNotice(c) ? 0 : c.overdue ? 1 : c.paymentStatus !== "PAID" ? 2 : 3
  return charges.filter(c => normalizeSearch(c.name).includes(query) && (filter === "all" || (!c.canceled && (
    filter === "review" ? Boolean(pendingFinanceNotice(c)) : filter === "paid" ? c.paymentStatus === "PAID" : c.paymentStatus !== "PAID"
  )))).sort((a, b) => priority(a) - priority(b) || a.name.localeCompare(b.name, "pt-BR"))
}

// Numbered pages: at least one page, so an empty list still reads "Página 1 de 1"
export function financePageCount(total: number, size: number): number {
  return Math.max(1, Math.ceil(total / size))
}

// After a change (a list closed, a movement reversed) the current page may no longer exist.
// Without a usable total (an API from before numbered pages) the page is kept, never NaN.
export function clampFinancePage(page: number, total: number | undefined, size: number): number {
  if (typeof total !== "number" || !Number.isFinite(total)) return page
  return Math.min(page, financePageCount(total, size) - 1)
}

export interface FinanceOptionsSummaryInput {
  dueDate?: string | null
  detailedPrice?: boolean
  componentCount: number
  dailyId?: number | null
  description?: string | null
  pixKey?: string | null
  pixRecipient?: string | null
}

// The closed "Vencimento e outras opções" section says what is set inside it, e.g. "Vence 15/11 · 3 itens · Pix próprio"
export function financeOptionsSummary(values: FinanceOptionsSummaryInput, defaults: { pixKey?: string | null; pixRecipient?: string | null } | undefined): string {
  const dueDate = values.dueDate?.trim()
  const pixKey = values.pixKey?.trim() ?? ""
  const pixRecipient = values.pixRecipient?.trim() ?? ""
  const ownPix = pixKey !== (defaults?.pixKey ?? "").trim() || pixRecipient !== (defaults?.pixRecipient ?? "").trim()
  return [
    dueDate ? `Vence ${dueDate.split("-").reverse().slice(0, 2).join("/")}` : "Sem vencimento",
    values.detailedPrice ? `${values.componentCount} ${values.componentCount === 1 ? "item" : "itens"}` : null,
    values.dailyId != null ? "Vinculada a uma sessão" : null,
    values.description?.trim() ? "Com observações" : null,
    !pixKey ? "Sem Pix" : ownPix ? "Pix próprio" : "Pix padrão",
  ].filter(Boolean).join(" · ")
}

// Why a charge can't be received right now, shown next to its disabled checkbox in selection mode; null when it can
// (the server refuses a receipt while a reported payment waits for review)
export function receiveChargeHint(charge: FinanceCharge): string | null {
  if (charge.canceled) return "Cancelada"
  if (charge.paymentStatus === "PAID" || charge.outstanding === "0.00") return "Já pago"
  if (pendingFinanceNotice(charge)) return "Confira o pagamento antes"
  return null
}
export function canReceiveCharge(charge: FinanceCharge): boolean {
  return receiveChargeHint(charge) === null
}

// "Receber selecionados": the selected charges that can be received, their total, and how many still owe but were
// left out because a reported payment arrived after they were selected and must be reviewed first
export function planReceiveAll(selected: FinanceCharge[]) {
  const charges = selected.filter(canReceiveCharge)
  const review = selected.filter(c => !c.canceled && c.paymentStatus !== "PAID" && c.outstanding !== "0.00" && pendingFinanceNotice(c)).length
  return { charges, total: sumFinanceAmounts(charges.map(c => c.outstanding)), review }
}

// "Ajustar valor": a charge can get a new composition unless it is canceled or a reported payment waits for review
// (paid charges included); null when it can be selected
export function adjustChargeHint(charge: FinanceCharge): string | null {
  if (charge.canceled) return "Cancelada"
  if (pendingFinanceNotice(charge)) return "Confira o pagamento antes"
  return null
}
export function canAdjustCharge(charge: FinanceCharge): boolean {
  return adjustChargeHint(charge) === null
}

// What each action of the selection bar changes, like planBulkActions for the members grid: Receber counts only
// the selected charges that can be received now (skipped: the others), Ajustar valor every adjustable one
export function planBulkFinanceActions(selected: FinanceCharge[]) {
  const receive = planReceiveAll(selected)
  const adjust = selected.filter(canAdjustCharge)
  return {
    receive: { ...receive, skipped: selected.length - receive.charges.length, hint: receive.charges.length === 0 ? "Nenhum selecionado pode receber agora" : null },
    adjust: { charges: adjust, hint: adjust.length === 0 ? "Nenhum selecionado pode ser ajustado" : null },
  }
}

// "Selecionar mensalistas": the adjustable charges of members who are mensalistas today (guests never match)
export function mensalistaCharges(charges: FinanceCharge[], members: { id: number; isMensalista: boolean }[]) {
  const mensalistas = new Set(members.filter(m => m.isMensalista).map(m => m.id))
  return charges.filter(c => c.userId !== null && mensalistas.has(c.userId) && canAdjustCharge(c))
}

const sameComposition = (charge: FinanceCharge, components: FinanceComponent[], adjustment: string) =>
  normalizeFinanceAmount(charge.adjustment) === normalizeFinanceAmount(adjustment) && charge.components.length === components.length &&
  charge.components.every((c, i) => c.name.trim() === components[i].name.trim() && normalizeFinanceAmount(c.amount) === normalizeFinanceAmount(components[i].amount))

// Live summary of the bulk adjust form: what each person will owe, the total, and how many of the selected
// charges had a composition of their own (different from the list's) that the new one replaces.
// Amounts are pt-BR input; an invalid one yields null for the amounts.
export function bulkAdjustSummary(values: { components: { amount: string }[]; adjustment: string }, charges: FinanceCharge[], list: { components: FinanceComponent[] }) {
  const amounts = [...values.components.map(c => c.amount), values.adjustment].map(normalizeFinanceAmount)
  const each = amounts.every((a): a is string => a !== null) ? sumFinanceAmounts(amounts) : null
  return {
    each,
    total: each === null ? null : sumFinanceAmounts(charges.map(() => each)),
    differs: charges.filter(c => !sameComposition(c, list.components, "0.00")).length,
  }
}
