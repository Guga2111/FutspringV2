import { useState, type Dispatch, type SetStateAction } from 'react'
import { toast } from 'sonner'
import {
  adminConfirmAllAttendance,
  adminConfirmAttendance,
  adminDisconfirmAttendance,
  confirmAttendance,
  disconfirmAttendance,
  markBumpsSeen,
  saveTeams as saveTeamsRequest,
  sortTeams,
  swapPlayers,
  updateDailyStatus,
  updateTeamColor,
  updateTeamName,
  uploadChampionImage,
} from '@/api/dailies'
import { useAuth } from '@/hooks/useAuth'
import { useNotificationsContext } from '@/hooks/useNotificationsContext'
import { withCallerAttendance } from '@/utils/attendance'
import { getErrorMessage, getErrorStatus } from '@/lib/errors'
import { validateImageFile } from '@/schemas/upload'
import type { DailyDetail, DailyStatus, TeamDTO } from '@/types/daily'
import type { SaveTeamsInput } from '@/api/dailies'

interface Options {
  daily: DailyDetail | null
  setDaily: Dispatch<SetStateAction<DailyDetail | null>>
  refetch: () => Promise<void>
}

// Mutations of the session page. Each one blocks its own button while pending, reports success, and either
// merges the returned DTO (status, team name/color) or reloads the detail when the endpoint returns less.
export function useDailyActions({ daily, setDaily, refetch }: Options) {
  const [confirmLoading, setConfirmLoading] = useState(false)
  const [sortLoading, setSortLoading] = useState(false)
  const [swapLoading, setSwapLoading] = useState(false)
  const [statusLoading, setStatusLoading] = useState(false)
  const [uploadLoading, setUploadLoading] = useState(false)
  const [adminToggleLoading, setAdminToggleLoading] = useState<number | null>(null)
  const [confirmAllLoading, setConfirmAllLoading] = useState(false)
  const [dismissBumpLoading, setDismissBumpLoading] = useState(false)
  const { markBumpRead } = useNotificationsContext()
  const { user } = useAuth()
  const [selectedPlayer, setSelectedPlayer] = useState<{ id: number; teamId: number } | null>(null)

  async function run(action: () => Promise<unknown>, success: string, failure: string, reload = true) {
    try {
      await action()
      if (reload) await refetch()
      toast.success(success)
      return true
    } catch (error) {
      toast.error(getErrorMessage(error, failure))
      return false
    }
  }

  function mergeTeam(teamId: number, updated: TeamDTO) {
    setDaily((prev) => (prev ? { ...prev, teams: prev.teams.map((t) => (t.id === teamId ? updated : t)) } : prev))
  }

  // Optimistic: the caller enters / leaves the confirmed list at once; the detail is then read again (teams, bumps,
  // counts), and an error puts the previous detail back
  async function toggleAttendance(confirmed: boolean) {
    if (!daily || !user) return
    setConfirmLoading(true)
    const previous = daily
    setDaily(withCallerAttendance(daily, user.id, !confirmed, new Date()))
    try {
      await (confirmed ? disconfirmAttendance(daily.id) : confirmAttendance(daily.id))
      await refetch()
    } catch (error) {
      setDaily(previous)
      toast.error(getErrorMessage(error, confirmed ? 'Não foi possível cancelar a presença' : 'Não foi possível confirmar a presença'))
    } finally {
      setConfirmLoading(false)
    }
  }

  async function adminToggle(userId: number, confirm: boolean) {
    if (!daily) return
    setAdminToggleLoading(userId)
    await (confirm
      ? run(() => adminConfirmAttendance(daily.id, userId), 'Jogador confirmado', 'Não foi possível confirmar o jogador')
      : run(() => adminDisconfirmAttendance(daily.id, userId), 'Jogador removido da lista', 'Não foi possível remover o jogador'))
    setAdminToggleLoading(null)
  }

  async function confirmAll() {
    if (!daily) return
    setConfirmAllLoading(true)
    await run(() => adminConfirmAllAttendance(daily.id), 'Todos os membros confirmados', 'Não foi possível confirmar todos')
    setConfirmAllLoading(false)
  }

  // Resolves to whether the sort worked (the page collapses the attendance list after it)
  async function handleSortTeams(): Promise<boolean> {
    if (!daily) return false
    setSortLoading(true)
    const ok = await run(() => sortTeams(daily.id), 'Times sorteados', 'Não foi possível sortear os times')
    setSortLoading(false)
    return ok
  }

  // First click selects a player, a click on a player of another team swaps them
  async function handlePlayerClick(playerId: number, teamId: number) {
    if (!daily) return
    if (selectedPlayer === null) {
      setSelectedPlayer({ id: playerId, teamId })
      return
    }
    if (selectedPlayer.id === playerId) {
      setSelectedPlayer(null)
      return
    }
    if (selectedPlayer.teamId === teamId) {
      toast.error('Selecione um jogador de outro time')
      return
    }
    setSwapLoading(true)
    const ok = await run(() => swapPlayers(daily.id, selectedPlayer.id, playerId), 'Jogadores trocados', 'Não foi possível trocar os jogadores')
    if (ok) setSelectedPlayer(null)
    setSwapLoading(false)
  }

  // A 409 (the teams can't start: attendance changed meanwhile) shows the server's reason and reloads the session
  async function changeStatus(targetStatus: DailyStatus): Promise<boolean> {
    if (!daily) return false
    setStatusLoading(true)
    try {
      const updated = await updateDailyStatus(daily.id, targetStatus)
      setDaily((prev) => (prev ? { ...prev, status: updated.status, isFinished: updated.isFinished } : prev))
      toast.success('Status atualizado')
      return true
    } catch (error) {
      toast.error(getErrorMessage(error, 'Não foi possível atualizar o status'))
      if (getErrorStatus(error) === 409) await refetch()
      return false
    } finally {
      setStatusLoading(false)
    }
  }

  // The teams editor is a form: it handles the error itself, so this rethrows instead of toasting it
  async function saveTeams(payload: SaveTeamsInput) {
    if (!daily) return
    const teams = await saveTeamsRequest(daily.id, payload)
    setDaily((prev) => (prev ? { ...prev, teams } : prev))
    setSelectedPlayer(null)
    toast.success('Times salvos')
  }

  async function uploadChampion(file: File) {
    if (!daily) return
    const invalid = validateImageFile(file)
    if (invalid) {
      toast.error(invalid)
      return
    }
    setUploadLoading(true)
    // The endpoint returns the list item (no image name), so reload the detail
    await run(() => uploadChampionImage(daily.id, file), 'Foto dos campeões enviada', 'Não foi possível enviar a foto')
    setUploadLoading(false)
  }

  async function changeTeamName(teamId: number, name: string) {
    if (!daily) return
    try {
      mergeTeam(teamId, await updateTeamName(daily.id, teamId, name))
    } catch (error) {
      toast.error(getErrorMessage(error, 'Não foi possível renomear o time'))
    }
  }

  async function changeTeamColor(teamId: number, color: string) {
    if (!daily) return
    try {
      mergeTeam(teamId, await updateTeamColor(daily.id, teamId, color))
    } catch (error) {
      toast.error(getErrorMessage(error, 'Não foi possível mudar a cor do time'))
    }
  }

  // The caller dismisses their "you lost your spot" alert
  async function dismissBump() {
    if (!daily) return
    setDismissBumpLoading(true)
    try {
      await markBumpsSeen(daily.id)
      setDaily((prev) => (prev ? { ...prev, myBump: null } : prev))
      markBumpRead(daily.id)
    } catch (error) {
      toast.error(getErrorMessage(error, 'Não foi possível fechar o aviso'))
    } finally {
      setDismissBumpLoading(false)
    }
  }

  return {
    confirmLoading,
    dismissBumpLoading,
    dismissBump,
    sortLoading,
    swapLoading,
    statusLoading,
    uploadLoading,
    adminToggleLoading,
    confirmAllLoading,
    selectedPlayer,
    toggleAttendance,
    adminToggle,
    confirmAll,
    handleSortTeams,
    handlePlayerClick,
    changeStatus,
    saveTeams,
    uploadChampion,
    changeTeamName,
    changeTeamColor,
  }
}
