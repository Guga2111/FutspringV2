import { Badge } from "@/components/ui/badge"
import { subscriptionStatusLabel, subscriptionStatusStyle, type SubscriptionStatus } from "@/types/subscription"

export function SubscriptionStatusBadge({ status }: { status: SubscriptionStatus }) {
  return (
    <Badge variant="status" className={subscriptionStatusStyle[status]}>
      {subscriptionStatusLabel[status]}
    </Badge>
  )
}
