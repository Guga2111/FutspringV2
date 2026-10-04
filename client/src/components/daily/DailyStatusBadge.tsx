import { Badge } from "@/components/ui/badge"
import { dailyStatusLabel, type DailyStatus } from "@/types/daily"

const variant: Record<DailyStatus, "default" | "secondary" | "destructive" | "outline"> = {
  SCHEDULED: "secondary",
  CONFIRMED: "outline",
  IN_COURSE: "default",
  FINISHED: "secondary",
  CANCELED: "destructive",
}

export function DailyStatusBadge({ status }: { status: DailyStatus }) {
  return <Badge variant={variant[status]}>{dailyStatusLabel[status]}</Badge>
}
