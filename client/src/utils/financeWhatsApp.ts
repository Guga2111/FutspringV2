import { normalizeSearch } from "@/lib/utils"
import type { FinanceCharge, FinanceComponent, FinanceDetail } from "@/types/finance"
import { formatFinanceMoney, pendingFinanceNotice, sumFinanceAmounts } from "@/utils/finance"
import { formatShortDate, parseLocalDate } from "@/utils/dates"

// wa.me cuts the link around 2,000 encoded characters (emoji and accents count several each)
export const WHATSAPP_URL_MAX = 2000

export function whatsAppUrl(text: string): string {
  return `https://wa.me/?text=${encodeURIComponent(text)}`
}

const byName = (a: FinanceCharge, b: FinanceCharge) => normalizeSearch(a.name).localeCompare(normalizeSearch(b.name), "pt-BR")

function sameComponents(a: FinanceComponent[], b: FinanceComponent[]): boolean {
  return a.length === b.length && a.every((c, i) => c.name === b[i].name && c.amount === b[i].amount)
}

// The WhatsApp message of an open collection, built from the detail on screen (plain text, *bold* in WhatsApp's syntax).
// Only managers build it, and they see every charge: it always names who owes and who paid, whatever the
// visibility members have in the app. today: "yyyy-MM-dd" in the pelada's zone
export function financeWhatsAppMessage(detail: FinanceDetail, link: string, today: string): string {
  const list = detail.collection
  const now = parseLocalDate(today)
  const blocks: string[] = []

  const head = [`*${list.name}*`]
  if (list.dueDate) head.push(`${list.dueDate < today ? "Venceu" : "Vence"} ${formatShortDate(list.dueDate, now)}`)
  blocks.push(head.join("\n"))

  const active = detail.charges.filter((c) => !c.canceled)
  const composition: string[] = []
  if (list.components.length === 1) composition.push(`Valor: ${formatFinanceMoney(list.components[0].amount)}`)
  if (list.components.length > 1) {
    for (const c of list.components) composition.push(`• ${c.name} — ${formatFinanceMoney(c.amount)}`)
    composition.push(`*Total: ${formatFinanceMoney(sumFinanceAmounts(list.components.map((c) => c.amount)))}*`)
  }
  if (active.some((c) => c.adjustment !== "0.00" || !sameComponents(c.components, list.components))) {
    composition.push("Alguns participantes têm valor diferente.")
  }
  if (composition.length > 0) blocks.push(composition.join("\n"))

  // someone with a payment under review is neither charged again nor counted as paid
  const reviewing = active.filter((c) => pendingFinanceNotice(c)).sort(byName)
  const settled = active.filter((c) => !pendingFinanceNotice(c))
  const debtors = settled.filter((c) => c.outstanding !== "0.00" && !c.outstanding.startsWith("-")).sort(byName)
  const paid = settled.filter((c) => c.paymentStatus === "PAID").sort(byName)
  const names = (charges: FinanceCharge[]) => charges.map((c) => c.name).join(", ")

  if (active.length > 0) {
    const status: string[] = []
    if (debtors.length === 0 && reviewing.length === 0) status.push("✅ Todos pagaram!")
    else {
      if (debtors.length > 0) {
        status.push(`⏳ Faltam pagar (${debtors.length}):`)
        debtors.forEach((c, i) => {
          status.push(`${i + 1}. ${c.name} — ${formatFinanceMoney(c.outstanding)}${c.paymentStatus === "PARTIAL" ? " (parcial)" : ""}${c.overdue ? " ⚠️ vencida" : ""}`)
        })
      }
      if (reviewing.length > 0) status.push(`🔎 Conferindo (${reviewing.length}): ${names(reviewing)}`)
      if (paid.length > 0) {
        status.push(`✅ Pagos (${paid.length}):`)
        paid.forEach((c, i) => status.push(`${i + 1}. ${c.name} — ${formatFinanceMoney(c.amountDue)}`))
      }
    }
    blocks.push(status.join("\n"))
  }

  if (list.pixKey) blocks.push(`Pix: ${list.pixKey}${list.pixRecipient ? ` (${list.pixRecipient})` : ""}`)
  blocks.push(`✅ Já pagou? Avise aqui: ${link}`)
  return blocks.join("\n\n")
}
