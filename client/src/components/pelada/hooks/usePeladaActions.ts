import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { deletePelada, removePlayer, setAdmin } from "@/api/peladas"
import { confirmAttendance, disconfirmAttendance } from "@/api/dailies"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { getErrorMessage } from "@/lib/errors"
import type { PeladaDetail, PeladaMember } from "@/types/pelada"
import type { DailyListItem } from "@/types/daily"

// Mutations of the pelada page: admin/creator actions (the pelada is reloaded after member changes) and the
// caller's attendance in the next session (merged into the sessions list and the sidebar's peladas)
export function usePeladaActions(
  pelada: PeladaDetail | null,
  refetchPelada: () => Promise<void>,
  mergeDaily: (item: DailyListItem) => void,
) {
  const navigate = useNavigate()
  const { updateNextDaily, reload: reloadMyPeladas } = useMyPeladasContext()
  const [attendancePending, setAttendancePending] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [removing, setRemoving] = useState(false)
  const [togglingAdmin, setTogglingAdmin] = useState<number | null>(null)

  async function removePeladaAndLeave(): Promise<boolean> {
    if (!pelada) return false
    setDeleting(true)
    try {
      await deletePelada(pelada.id)
      toast.success("Pelada excluída")
      navigate("/home")
      return true
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível excluir a pelada"))
      setDeleting(false)
      return false
    }
  }

  async function removeMember(member: PeladaMember): Promise<boolean> {
    if (!pelada) return false
    setRemoving(true)
    try {
      await removePlayer(pelada.id, member.id)
      await refetchPelada()
      toast.success(`${member.username} foi removido da pelada`)
      return true
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível remover o jogador"))
      return false
    } finally {
      setRemoving(false)
    }
  }

  async function toggleAdmin(member: PeladaMember) {
    if (!pelada) return
    setTogglingAdmin(member.id)
    try {
      await setAdmin(pelada.id, member.id, !member.isAdmin)
      await refetchPelada()
      toast.success(member.isAdmin ? `${member.username} não é mais administrador` : `${member.username} agora é administrador`)
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível alterar o administrador"))
    } finally {
      setTogglingAdmin(null)
    }
  }

  async function toggleAttendance(daily: DailyListItem) {
    if (!pelada) return
    setAttendancePending(true)
    try {
      const updated = daily.isConfirmed ? await disconfirmAttendance(daily.id) : await confirmAttendance(daily.id)
      mergeDaily(updated)
      updateNextDaily(pelada.id, { confirmedCount: updated.confirmedPlayerCount, isConfirmed: updated.isConfirmed })
      toast.success(updated.isConfirmed ? "Presença confirmada" : "Presença cancelada")
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível atualizar a presença"))
    } finally {
      setAttendancePending(false)
    }
  }

  async function removePeladaAndLeaveAndReload(): Promise<boolean> {
    const removed = await removePeladaAndLeave()
    if (removed) reloadMyPeladas()
    return removed
  }

  return {
    deleting,
    removing,
    togglingAdmin,
    attendancePending,
    removePeladaAndLeave: removePeladaAndLeaveAndReload,
    removeMember,
    toggleAdmin,
    toggleAttendance,
  }
}
