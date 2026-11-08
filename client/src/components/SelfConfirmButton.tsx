import type { ReactNode } from "react"
import { Button } from "@/components/ui/button"
import { useSelfConfirm } from "@/hooks/useSelfConfirm"
import { cn } from "@/lib/utils"
import { READ_ONLY_HINT } from "@/utils/subscription"

interface SelfConfirmButtonProps {
  confirmOpensAt: string | null
  confirmedCount: number
  capacity: number
  timeZone: string
  pending: boolean
  onConfirm: () => void
  // the pelada is read-only (subscription): confirming is locked, withdrawing isn't
  readOnly?: boolean
  className?: string
  children?: ReactNode
}

// "Confirmar presença" for a caller who hasn't confirmed: disabled with the reason before the diarista window
// opens, when the session is full or the pelada is read-only, with a hint when a mensalista enters a full session
export function SelfConfirmButton({
  confirmOpensAt,
  confirmedCount,
  capacity,
  timeZone,
  pending,
  onConfirm,
  readOnly = false,
  className,
  children = "Confirmar presença",
}: SelfConfirmButtonProps) {
  const confirm = useSelfConfirm({ isConfirmed: false, confirmOpensAt, confirmedCount, capacity }, timeZone)
  const closedLabel = readOnly ? READ_ONLY_HINT : confirm.closedLabel
  const { hint } = confirm

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
