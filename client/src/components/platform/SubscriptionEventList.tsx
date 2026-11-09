import type { PlatformEvent } from "@/types/platform"
import { eventLine, formatBillingDate } from "@/utils/platform"

// The subscription's history, newest first: "Leal · Pagamento registrado · validade 07/12/2026 → 07/12/2027"
export function SubscriptionEventList({ events }: { events: PlatformEvent[] }) {
  if (events.length === 0) return <p className="text-sm text-muted-foreground">Nenhum evento.</p>
  return (
    <ol className="flex flex-col divide-y divide-border">
      {events.map((event) => (
        <li key={event.id} className="flex flex-col gap-0.5 py-3 first:pt-0 last:pb-0">
          <span className="text-sm">{eventLine(event)}</span>
          <span className="text-caption text-muted-foreground">
            {formatBillingDate(event.createdAt)}
            {event.note && ` · ${event.note}`}
          </span>
        </li>
      ))}
    </ol>
  )
}
