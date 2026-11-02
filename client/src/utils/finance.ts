import type { FinanceCharge } from "@/types/finance"

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
  const normalize = (value: string) => value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("pt-BR")
  const query = normalize(search.trim())
  const priority = (c: FinanceCharge) => c.canceled ? 4 : pendingFinanceNotice(c) ? 0 : c.overdue ? 1 : c.paymentStatus !== "PAID" ? 2 : 3
  return charges.filter(c => normalize(c.name).includes(query) && (filter === "all" || (!c.canceled && (
    filter === "review" ? Boolean(pendingFinanceNotice(c)) : filter === "paid" ? c.paymentStatus === "PAID" : c.paymentStatus !== "PAID"
  )))).sort((a, b) => priority(a) - priority(b) || a.name.localeCompare(b.name, "pt-BR"))
}

// Numbered pages: at least one page, so an empty list still reads "Página 1 de 1"
export function financePageCount(total: number, size: number): number {
  return Math.max(1, Math.ceil(total / size))
}

// After a change (a list closed, a movement reversed) the current page may no longer exist
export function clampFinancePage(page: number, total: number, size: number): number {
  return Math.min(page, financePageCount(total, size) - 1)
}
