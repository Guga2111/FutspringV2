import type { ReactNode } from "react"
import { Button } from "@/components/ui/button"
import { useSelfConfirm } from "@/hooks/useSelfConfirm"
import { cn } from "@/lib/utils"

interface SelfConfirmButtonProps {
  confirmOpensAt: string | null
  confirmedCount: number
  capacity: number
  timeZone: string
  pending: boolean
  onConfirm: () => void
  className?: string
  children?: ReactNode
}

// "Confirmar presença" for a caller who hasn't confirmed: disabled with the reason before the diarista window
// opens or when the session is full, with a hint when a mensalista enters a full session
export function SelfConfirmButton({
  confirmOpensAt,
  confirmedCount,
  capacity,
  timeZone,
  pending,
  onConfirm,
  className,
  children = "Confirmar presença",
}: SelfConfirmButtonProps) {
  const { closedLabel, hint } = useSelfConfirm({ isConfirmed: false, confirmOpensAt, confirmedCount, capacity }, timeZone)

  if (closedLabel) {
    return (
      <Button variant="outline" disabled className={cn("bg-transparent font-semibold", className)}>
        {closedLabel}
      </Button>
    )
  }
  return (
    <div className="flex flex-col items-stretch gap-1">
      <Button variant="brand" disabled={pending} onClick={onConfirm} className={cn("font-semibold", className)}>
        {children}
      </Button>
      {hint && <span className="text-xs text-muted-foreground">{hint}</span>}
    </div>
  )
}
