import { HugeiconsIcon } from "@hugeicons/react"
import { UserRemove01Icon } from "@hugeicons/core-free-icons"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { formatInZone } from "@/utils/dates"
import type { AttendanceBump } from "@/types/daily"

interface BumpAlertProps {
  bump: AttendanceBump
  timeZone: string
  // shown on Home, where the alert isn't inside the session's page
  peladaName?: string
  pending: boolean
  onDismiss: () => void
}

// "You lost your spot": shown to a diarista bumped by a mensalista until they dismiss it
export function BumpAlert({ bump, timeZone, peladaName, pending, onDismiss }: BumpAlertProps) {
  return (
    <Alert className="flex flex-col gap-3 border-destructive-muted md:flex-row md:items-center">
      <HugeiconsIcon icon={UserRemove01Icon} className="size-4 text-destructive-soft" />
      <div className="flex-1">
        <AlertTitle>Você perdeu a vaga{peladaName ? ` em ${peladaName}` : ""}</AlertTitle>
        <AlertDescription className="text-muted-foreground">
          {bump.bumpedBy.username} (mensalista) confirmou com a sessão lotada em {formatInZone(bump.bumpedAt, timeZone)}.
          Se abrir uma vaga, você pode confirmar de novo.
        </AlertDescription>
      </div>
      <Button variant="outline" size="sm" disabled={pending} onClick={onDismiss} className="self-start md:self-center">
        Entendi
      </Button>
    </Alert>
  )
}
