import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import { getPositionLabel } from "@/types/user"

// A player's position (members grid, attendance list, profile), from the one label map in types/user
export function PositionBadge({ position, className }: { position: string | null | undefined; className?: string }) {
  const label = getPositionLabel(position)
  if (!label) return null
  return (
    <Badge variant="status" className={cn("bg-secondary px-2 py-0.5 text-[11px] font-medium text-secondary-foreground", className)}>
      {label}
    </Badge>
  )
}
