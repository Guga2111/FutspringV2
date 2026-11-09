import { Link } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowRight01Icon } from "@hugeicons/core-free-icons"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import type { PlatformAttention, PlatformOverview } from "@/types/platform"
import { formatBillingDate } from "@/utils/platform"

function Group({ title, items, dateLabel }: { title: string; items: PlatformAttention[]; dateLabel: string }) {
  if (items.length === 0) return null
  return (
    <section className="flex flex-col gap-1">
      <h3 className="text-overline font-semibold uppercase tracking-wide text-subtle-foreground">
        {title} ({items.length})
      </h3>
      <ul className="flex flex-col">
        {items.map((item) => (
          <li key={item.id}>
            <Link
              to={`/admin/peladas/${item.id}`}
              className="-mx-2 flex items-center justify-between gap-3 rounded-md px-2 py-2 text-sm hover:bg-row-hover"
            >
              <span className="min-w-0 truncate font-medium">{item.name}</span>
              <span className="shrink-0 text-caption text-muted-foreground">
                {dateLabel} {formatBillingDate(item.date)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

// "Precisa de você": Pix informed, trials ending in 7 days and the peladas past due, each linking to its page
export function AttentionList({ overview }: { overview: PlatformOverview }) {
  const pastDue = overview.counts.PAST_DUE
  const empty = overview.pixClaimed.length === 0 && overview.trialsEndingSoon.length === 0 && pastDue === 0
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-title">Precisa de você</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {empty ? (
          <p className="text-sm text-muted-foreground">Nada pendente agora.</p>
        ) : (
          <>
            <Group title="Pix informados" items={overview.pixClaimed} dateLabel="informado em" />
            <Group title="Testes vencendo" items={overview.trialsEndingSoon} dateLabel="até" />
            {pastDue > 0 && (
              <Link
                to="/admin/peladas?estado=PAST_DUE"
                className="-mx-2 flex items-center justify-between gap-3 rounded-md px-2 py-2 text-sm font-medium hover:bg-row-hover"
              >
                {pastDue} {pastDue === 1 ? "pelada em atraso" : "peladas em atraso"}
                <HugeiconsIcon icon={ArrowRight01Icon} className="size-4 text-muted-foreground" />
              </Link>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}
