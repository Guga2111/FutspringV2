import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { deletePelada, removePlayer, setAdmin, setMensalista, setPlayerStars } from "@/api/peladas"
import { confirmAttendance, disconfirmAttendance } from "@/api/dailies"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { getErrorMessage } from "@/lib/errors"
import type { PeladaDetail, PeladaMember } from "@/types/pelada"
import type { DailyListItem } from "@/types/daily"

// Mutations of the pelada page: admin/creator actions (member changes are merged into the pelada on screen; the
// dialogs close before the request, which runs in the background with a toast) and the caller's attendance in
// the next session (merged into the sessions list and the sidebar's peladas)
export function usePeladaActions(
  pelada: PeladaDetail | null,
  refetchDailies: () => Promise<void>,
  mergeDaily: (item: DailyListItem) => void,
  mergeMember: (member: PeladaMember) => void,
  dropMember: (userId: number) => void,
) {
  const navigate = useNavigate()
  const { updateNextDaily, reload: reloadMyPeladas } = useMyPeladasContext()
  const [attendancePending, setAttendancePending] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [removingMember, setRemovingMember] = useState<number | null>(null)
  const [settingStars, setSettingStars] = useState<number | null>(null)
  const [togglingAdmin, setTogglingAdmin] = useState<number | null>(null)
  const [togglingMensalista, setTogglingMensalista] = useState<number | null>(null)

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

  // The card stays dimmed until the response; the backend also takes the member out of upcoming sessions
  async function removeMember(member: PeladaMember) {
    if (!pelada) return
    setRemovingMember(member.id)
    try {
      await removePlayer(pelada.id, member.id)
      dropMember(member.id)
      toast.success(`${member.username} foi removido da pelada`)
      void refetchDailies()
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível remover o jogador"))
    } finally {
      setRemovingMember(null)
    }
  }

  async function toggleAdmin(member: PeladaMember) {
    if (!pelada) return
    setTogglingAdmin(member.id)
    try {
      await setAdmin(pelada.id, member.id, !member.isAdmin)
      mergeMember({ ...member, isAdmin: !member.isAdmin })
      toast.success(member.isAdmin ? `${member.username} não é mais administrador` : `${member.username} agora é administrador`)
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível alterar o administrador"))
    } finally {
      setTogglingAdmin(null)
    }
  }

  // Optimistic: the card shows the new stars at once and goes back to the previous value if the request fails
  async function setMemberStars(member: PeladaMember, stars: number) {
    if (!pelada) return
    setSettingStars(member.id)
    mergeMember({ ...member, stars })
    try {
      mergeMember(await setPlayerStars(pelada.id, member.id, stars))
      toast.success(`Estrelas de ${member.username} atualizadas`)
    } catch (error) {
      mergeMember(member)
      toast.error(getErrorMessage(error, "Não foi possível salvar as estrelas"))
    } finally {
      setSettingStars(null)
    }
  }

  async function setMemberMensalista(member: PeladaMember, isMensalista: boolean) {
    if (!pelada) return
    setTogglingMensalista(member.id)
    try {
      mergeMember(await setMensalista(pelada.id, member.id, isMensalista))
      toast.success(isMensalista ? `${member.username} agora é mensalista` : `${member.username} agora é diarista`)
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível alterar o tipo do jogador"))
    } finally {
      setTogglingMensalista(null)
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
    removingMember,
    settingStars,
    togglingAdmin,
    togglingMensalista,
    attendancePending,
    removePeladaAndLeave: removePeladaAndLeaveAndReload,
    removeMember,
    toggleAdmin,
    setMemberStars,
    setMemberMensalista,
    toggleAttendance,
  }
}
