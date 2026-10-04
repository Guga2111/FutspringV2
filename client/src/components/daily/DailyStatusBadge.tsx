import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import { dailyStatusLabel, dailyStatusStyle, type DailyStatus } from "@/types/daily"

type BadgeSize = "sm" | "md" | "lg"

// sm: cards (11px), md: lists (12px), lg: page header (12px, wider)
const sizeClass: Record<BadgeSize, { badge: string; dot: string }> = {
  sm: { badge: "gap-[5px] px-[7px] py-0.5 text-[11px]", dot: "size-[5px]" },
  md: { badge: "gap-1.5 px-2 py-0.5 text-xs", dot: "size-1.5" },
  lg: { badge: "gap-1.5 px-2.5 py-[3px] text-xs", dot: "size-1.5" },
}

interface DailyStatusBadgeProps {
  status: DailyStatus
  size?: BadgeSize
  className?: string
}

export function DailyStatusBadge({ status, size = "md", className }: DailyStatusBadgeProps) {
  const style = dailyStatusStyle[status]
  return (
    <Badge
      variant="status"
      className={cn(sizeClass[size].badge, style.badge, className)}
    >
      <span aria-hidden className={cn("shrink-0 rounded-full", sizeClass[size].dot, style.dot)} />
      {dailyStatusLabel[status]}
    </Badge>
  )
}
