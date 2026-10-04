import { useState, type Dispatch, type SetStateAction } from 'react'
import { toast } from 'sonner'
import {
  adminConfirmAttendance,
  adminDisconfirmAttendance,
  confirmAttendance,
  disconfirmAttendance,
  sortTeams,
  swapPlayers,
  updateDailyStatus,
  updateTeamColor,
  updateTeamName,
  uploadChampionImage,
} from '@/api/dailies'
import { getErrorMessage } from '@/lib/errors'
import { validateImageFile } from '@/schemas/upload'
import type { DailyDetail, DailyStatus, TeamDTO } from '@/types/daily'

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

  async function toggleAttendance(confirmed: boolean) {
    if (!daily) return
    setConfirmLoading(true)
    await (confirmed
      ? run(() => disconfirmAttendance(daily.id), 'Presença cancelada', 'Não foi possível cancelar a presença')
      : run(() => confirmAttendance(daily.id), 'Presença confirmada', 'Não foi possível confirmar a presença'))
    setConfirmLoading(false)
  }

  async function adminToggle(userId: number, confirm: boolean) {
    if (!daily) return
    setAdminToggleLoading(userId)
    await (confirm
      ? run(() => adminConfirmAttendance(daily.id, userId), 'Jogador confirmado', 'Não foi possível confirmar o jogador')
      : run(() => adminDisconfirmAttendance(daily.id, userId), 'Jogador removido da lista', 'Não foi possível remover o jogador'))
    setAdminToggleLoading(null)
  }

  async function handleSortTeams() {
    if (!daily) return
    setSortLoading(true)
    await run(() => sortTeams(daily.id), 'Times sorteados', 'Não foi possível sortear os times')
    setSortLoading(false)
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

  async function changeStatus(targetStatus: DailyStatus): Promise<boolean> {
    if (!daily) return false
    setStatusLoading(true)
    const ok = await run(async () => {
      const updated = await updateDailyStatus(daily.id, targetStatus)
      setDaily((prev) => (prev ? { ...prev, status: updated.status, isFinished: updated.isFinished } : prev))
    }, 'Status atualizado', 'Não foi possível atualizar o status', false)
    setStatusLoading(false)
    return ok
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

  return {
    confirmLoading,
    sortLoading,
    swapLoading,
    statusLoading,
    uploadLoading,
    adminToggleLoading,
    selectedPlayer,
    toggleAttendance,
    adminToggle,
    handleSortTeams,
    handlePlayerClick,
    changeStatus,
    uploadChampion,
    changeTeamName,
    changeTeamColor,
  }
}
