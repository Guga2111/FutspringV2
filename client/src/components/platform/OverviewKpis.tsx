import { Link } from "react-router-dom"
import { Card } from "@/components/ui/card"
import type { PlatformOverview } from "@/types/platform"
import type { SubscriptionStatus } from "@/types/subscription"
import { formatFinanceMoney } from "@/utils/finance"

interface Kpi {
  label: string
  value: string
  hint?: string
  // the peladas in that state
  to?: string
}

const byState = (state: SubscriptionStatus) => `/admin/peladas?estado=${state}`

// The panel's numbers: one card, a grid of tiles (2 columns below md)
export function OverviewKpis({ overview }: { overview: PlatformOverview }) {
  const { counts, users } = overview
  const kpis: Kpi[] = [
    { label: "Pagantes", value: String(counts.ACTIVE), to: byState("ACTIVE") },
    { label: "Em teste", value: String(counts.TRIAL), to: byState("TRIAL") },
    { label: "Em atraso", value: String(counts.PAST_DUE), to: byState("PAST_DUE") },
    { label: "Somente leitura", value: String(counts.READ_ONLY), hint: `${counts.PENDING} ${counts.PENDING === 1 ? "pendente" : "pendentes"}`, to: byState("READ_ONLY") },
    { label: "Receita do mês", value: formatFinanceMoney(overview.revenueThisMonth), hint: `Recorrente ${formatFinanceMoney(overview.recurringEstimate)}/mês` },
    { label: "Usuários", value: String(users.total), hint: `+${users.last30Days} em 30 dias` },
  ]
  return (
    <Card className="grid grid-cols-2 gap-px overflow-hidden bg-border p-0 md:grid-cols-3 lg:grid-cols-6">
      {kpis.map((kpi) => {
        const body = (
          <>
            <span className="text-caption font-medium text-muted-foreground">{kpi.label}</span>
            <span className="font-display text-heading font-bold tabular-nums md:text-heading-lg">{kpi.value}</span>
            {kpi.hint && <span className="text-caption text-muted-foreground">{kpi.hint}</span>}
          </>
        )
        const className = "flex min-w-0 flex-col gap-1 bg-card p-4"
        return kpi.to ? (
          <Link key={kpi.label} to={kpi.to} className={`${className} transition-colors hover:bg-row-hover`}>
            {body}
          </Link>
        ) : (
          <div key={kpi.label} className={className}>
            {body}
          </div>
        )
      })}
    </Card>
  )
}
