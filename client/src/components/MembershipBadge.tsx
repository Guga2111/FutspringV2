import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

// "Mensalista" / "Diarista" tag of a pelada member (members grid, attendance list)
export function MembershipBadge({ isMensalista, className }: { isMensalista: boolean; className?: string }) {
  return (
    <Badge variant={isMensalista ? "mensalista" : "diarista"} className={cn("px-2 py-0.5 text-[11px]", className)}>
      {isMensalista ? "Mensalista" : "Diarista"}
    </Badge>
  )
}
