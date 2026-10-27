import { useState } from "react"
import { toast } from "sonner"
import { confirmAttendance, disconfirmAttendance, markBumpsSeen } from "@/api/dailies"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { getErrorMessage } from "@/lib/errors"
import type { NextDaily } from "@/types/pelada"

// Confirm / withdraw from a pelada's next session on the Home cards; merged into the shared peladas
export function useUpcomingAttendance() {
  const { updateNextDaily } = useMyPeladasContext()
  const [pendingId, setPendingId] = useState<number | null>(null)

  async function toggle(peladaId: number, next: NextDaily) {
    setPendingId(next.id)
    try {
      const updated = next.isConfirmed ? await disconfirmAttendance(next.id) : await confirmAttendance(next.id)
      updateNextDaily(peladaId, { confirmedCount: updated.confirmedPlayerCount, isConfirmed: updated.isConfirmed })
      toast.success(updated.isConfirmed ? "Presença confirmada" : "Presença cancelada")
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível atualizar a presença"))
    } finally {
      setPendingId(null)
    }
  }

  // Dismisses the caller's "you lost your spot" alert of that session
  async function dismissBump(peladaId: number, next: NextDaily) {
    setPendingId(next.id)
    try {
      await markBumpsSeen(next.id)
      updateNextDaily(peladaId, { bump: null })
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível fechar o aviso"))
    } finally {
      setPendingId(null)
    }
  }

  return { pendingId, toggle, dismissBump }
}
