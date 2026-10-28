import { useState } from "react"
import { toast } from "sonner"
import {
  removePlayer,
  removePlayers,
  setAdmin,
  setAdmins,
  setMensalista,
  setMensalistas,
  setPlayerStars,
  setPlayersStars,
} from "@/api/peladas"
import { confirmAttendance, disconfirmAttendance } from "@/api/dailies"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { getErrorMessage } from "@/lib/errors"
import { pluralMembers } from "@/utils/bulkMemberActions"
import type { PeladaDetail, PeladaMember } from "@/types/pelada"
import type { DailyListItem } from "@/types/daily"

// Mutations of the pelada page: admin actions on one member or a selection (member changes are merged into
// the pelada on screen; the dialogs close before the request, which runs in the background with a toast) and the
// caller's attendance in the next session (merged into the sessions list and the sidebar's peladas)
export function usePeladaActions(
  pelada: PeladaDetail | null,
  refetchDailies: () => Promise<void>,
  mergeDaily: (item: DailyListItem) => void,
  mergeMembers: (members: PeladaMember[]) => void,
  dropMembers: (userIds: number[]) => void,
) {
  const { updateNextDaily } = useMyPeladasContext()
  const [attendancePending, setAttendancePending] = useState(false)
  const [removingMember, setRemovingMember] = useState<number | null>(null)
  const [settingStars, setSettingStars] = useState<number | null>(null)
  const [togglingAdmin, setTogglingAdmin] = useState<number | null>(null)
  const [togglingMensalista, setTogglingMensalista] = useState<number | null>(null)
  // the members a bulk request is changing: their cards are dimmed and the selection bar is disabled
  const [bulkPending, setBulkPending] = useState<ReadonlySet<number> | null>(null)
  const mergeMember = (member: PeladaMember) => mergeMembers([member])

  // The card stays dimmed until the response; the backend also takes the member out of upcoming sessions
  async function removeMember(member: PeladaMember) {
    if (!pelada) return
    setRemovingMember(member.id)
    try {
      await removePlayer(pelada.id, member.id)
      dropMembers([member.id])
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

  // Runs one bulk request; returns whether it succeeded so the grid leaves selection mode only then
  async function runBulk(
    targets: PeladaMember[],
    request: (peladaId: number, ids: number[]) => Promise<void>,
    errorMessage: string,
  ): Promise<boolean> {
    if (!pelada || targets.length === 0) return false
    const ids = targets.map((m) => m.id)
    setBulkPending(new Set(ids))
    try {
      await request(pelada.id, ids)
      return true
    } catch (error) {
      toast.error(getErrorMessage(error, errorMessage))
      return false
    } finally {
      setBulkPending(null)
    }
  }

  function bulkSetAdmin(targets: PeladaMember[], isAdmin: boolean) {
    return runBulk(
      targets,
      async (peladaId, ids) => {
        mergeMembers(await setAdmins(peladaId, ids, isAdmin))
        const who = pluralMembers(ids.length)
        const one = ids.length === 1
        toast.success(
          isAdmin
            ? `${who} agora ${one ? "é admin" : "são admins"}`
            : `${who} ${one ? "não é mais admin" : "não são mais admins"}`,
        )
      },
      "Não foi possível alterar os administradores",
    )
  }

  function bulkSetMensalista(targets: PeladaMember[], isMensalista: boolean) {
    return runBulk(
      targets,
      async (peladaId, ids) => {
        mergeMembers(await setMensalistas(peladaId, ids, isMensalista))
        const type = isMensalista ? "mensalista" : "diarista"
        toast.success(`${pluralMembers(ids.length)} agora ${ids.length === 1 ? `é ${type}` : `são ${type}s`}`)
      },
      "Não foi possível alterar o tipo dos jogadores",
    )
  }

  // Not optimistic (unlike the single action): only the response is merged
  function bulkSetStars(targets: PeladaMember[], stars: number) {
    return runBulk(
      targets,
      async (peladaId, ids) => {
        mergeMembers(await setPlayersStars(peladaId, ids, stars))
        toast.success(`Estrelas de ${pluralMembers(ids.length)} atualizadas`)
      },
      "Não foi possível salvar as estrelas",
    )
  }

  // The backend also takes them out of the sessions that haven't started
  function bulkRemove(targets: PeladaMember[]) {
    return runBulk(
      targets,
      async (peladaId, ids) => {
        await removePlayers(peladaId, ids)
        dropMembers(ids)
        toast.success(`${pluralMembers(ids.length)} ${ids.length === 1 ? "removido" : "removidos"} da pelada`)
        void refetchDailies()
      },
      "Não foi possível remover os jogadores",
    )
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

  return {
    removingMember,
    settingStars,
    togglingAdmin,
    togglingMensalista,
    bulkPending,
    attendancePending,
    removeMember,
    toggleAdmin,
    setMemberStars,
    setMemberMensalista,
    bulkSetAdmin,
    bulkSetMensalista,
    bulkSetStars,
    bulkRemove,
    toggleAttendance,
  }
}
