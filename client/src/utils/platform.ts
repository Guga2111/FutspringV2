import type { MonthRevenue, PlatformEvent } from "@/types/platform"
import { subscriptionEventLabel } from "@/types/platform"
import { formatFinanceMoney } from "@/utils/finance"

// the zone of the billing dates (mirrors SubscriptionService.BILLING_ZONE)
export const BILLING_ZONE = "America/Sao_Paulo"

const MONTHS = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"]

// "07/12/2026": an instant as a date in the billing zone
export function formatBillingDate(instant: string): string {
  return new Intl.DateTimeFormat("pt-BR", { timeZone: BILLING_ZONE, day: "2-digit", month: "2-digit", year: "numeric" })
    .format(new Date(instant))
}

// "07/12" in São Paulo, inside a sentence
export function formatBillingDay(instant: string | number): string {
  return new Intl.DateTimeFormat("pt-BR", { timeZone: BILLING_ZONE, day: "2-digit", month: "2-digit" })
    .format(new Date(instant))
}

// "07/12/2026" from a calendar date (yyyy-MM-dd)
export function formatIsoDate(date: string): string {
  const [year, month, day] = date.split("-")
  return `${day}/${month}/${year}`
}

// "validade 07/12/2026 → 07/12/2027", "validade até 07/12/2026" (none before), "sem validade" (none after)
function validityChange(before: string | null, after: string | null): string {
  if (!after) return "sem validade"
  if (!before) return `validade até ${formatBillingDate(after)}`
  return `validade ${formatBillingDate(before)} → ${formatBillingDate(after)}`
}

// "Leal · Pagamento registrado · validade 07/12/2026 → 07/12/2027"; the validity only when it changed
export function eventLine(event: PlatformEvent): string {
  const parts = [event.actor?.username ?? "Sistema", subscriptionEventLabel[event.type]]
  if (event.validUntilBefore !== event.validUntilAfter) parts.push(validityChange(event.validUntilBefore, event.validUntilAfter))
  return parts.join(" · ")
}

// "R$ 398,00 em 2 pagamentos"
export function paymentsSummary(total: number, sum: string): string {
  return `${formatFinanceMoney(sum)} em ${total} ${total === 1 ? "pagamento" : "pagamentos"}`
}

export interface RevenuePoint {
  // "nov/26"
  label: string
  // the axis' number (reais, two decimals); `total` keeps the exact string for the tooltip
  value: number
  total: string
}

export function revenueChartData(months: MonthRevenue[]): RevenuePoint[] {
  return months.map(({ month, total }) => {
    const [year, number] = month.split("-")
    return { label: `${MONTHS[Number(number) - 1]}/${year.slice(2)}`, value: Number(total), total }
  })
}
