import { useRef, useState } from 'react'
import { CalendarCheck, CalendarX } from 'lucide-react'
import { useParams, Link } from 'react-router-dom'
import { toast } from 'sonner'
import NavBar from '../components/NavBar'
import { confirmAttendance, disconfirmAttendance, adminConfirmAttendance, adminDisconfirmAttendance, sortTeams, swapPlayers, updateDailyStatus, uploadChampionImage, updateTeamName, updateTeamColor } from '../api/dailies'
import ImportFromMessageModal from '../components/daily/ImportFromMessageModal'
import { useAuth } from '../hooks/useAuth'
import { Button } from '../components/ui/button'
import { Badge } from '../components/ui/badge'
import DetailSkeleton from '../components/daily/DetailSkeleton'
import TeamsSection from '../components/daily/TeamsSection'
import ResultsModal from '../components/daily/ResultsModal'
import FinalizeModal from '../components/daily/FinalizeModal'
import ConfirmedPlayersSection from '../components/daily/ConfirmedPlayersSection'
import AdminActionBar from '../components/daily/AdminActionBar'
import StatusConfirmDialog from '../components/daily/StatusConfirmDialog'
import LiveSessionCard from '../components/daily/LiveSessionCard'
import MatchResultsSection from '../components/daily/MatchResultsSection'
import LeagueTableSection from '../components/daily/LeagueTableSection'
import PlayerStatsSection from '../components/daily/PlayerStatsSection'
import AwardsSection from '../components/daily/AwardsSection'
import ChampionPhotoSection from '../components/daily/ChampionPhotoSection'
import { useDailyDetail } from '../components/daily/hooks/useDailyDetail'

export default function DailyDetailPage() {
  const { id } = useParams<{ id: string }>()
  const dailyId = Number(id)
  const { user } = useAuth()

  const { daily, setDaily, loading, accessDenied, formattedDate, refetch: fetchDaily } = useDailyDetail(dailyId)

  const [confirmLoading, setConfirmLoading] = useState(false)
  const [sortLoading, setSortLoading] = useState(false)
  const [selectedPlayer, setSelectedPlayer] = useState<{ id: number; teamId: number } | null>(null)
  const [swapLoading, setSwapLoading] = useState(false)
  const [statusDialog, setStatusDialog] = useState<{ targetStatus: string; description: string } | null>(null)
  const [statusLoading, setStatusLoading] = useState(false)
  const [resultsOpen, setResultsOpen] = useState(false)
  const [finalizeOpen, setFinalizeOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [uploadLoading, setUploadLoading] = useState(false)
  const [adminToggleLoading, setAdminToggleLoading] = useState<number | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleConfirm = async () => {
    if (!daily) return
    setConfirmLoading(true)
    try {
      await confirmAttendance(daily.id)
      toast.success('Presença Confirmada!')
      fetchDaily()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao confirmar presença')
    } finally {
      setConfirmLoading(false)
    }
  }

  const handleSortTeams = async () => {
    if (!daily) return
    setSortLoading(true)
    try {
      await sortTeams(daily.id)
      await fetchDaily()
      toast.success('Times sorteados!')
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao sortear times')
    } finally {
      setSortLoading(false)
    }
  }

  const handlePlayerClick = async (playerId: number, teamId: number) => {
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
      toast.error('Selecione um jogador de um time diferente')
      return
    }
    setSwapLoading(true)
    try {
      await swapPlayers(daily.id, selectedPlayer.id, playerId)
      await fetchDaily()
      setSelectedPlayer(null)
      toast.success('Jogadores trocados!')
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao trocar jogadores')
    } finally {
      setSwapLoading(false)
    }
  }

  const handleDisconfirm = async () => {
    if (!daily) return
    setConfirmLoading(true)
    try {
      await disconfirmAttendance(daily.id)
      toast.success('Presença removida.')
      fetchDaily()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao remover presença')
    } finally {
      setConfirmLoading(false)
    }
  }

  const handleStatusConfirm = async () => {
    if (!daily || !statusDialog) return
    setStatusLoading(true)
    try {
      const updated = await updateDailyStatus(daily.id, statusDialog.targetStatus)
      setDaily((prev) => prev ? { ...prev, status: updated.status, isFinished: updated.isFinished } : prev)
      toast.success('Status atualizado!')
      setStatusDialog(null)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao atualizar status')
    } finally {
      setStatusLoading(false)
    }
  }

  const handleChampionUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !daily) return
    setUploadLoading(true)
    try {
      await uploadChampionImage(daily.id, file)
      toast.success('Foto do campeão enviada!')
      fetchDaily()
    } catch (err: unknown) {
      const ex = err as { response?: { data?: { message?: string } } }
      toast.error(ex?.response?.data?.message ?? 'Falha ao enviar foto')
    } finally {
      setUploadLoading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleAdminConfirm = async (userId: number) => {
    if (!daily) return
    setAdminToggleLoading(userId)
    try {
      await adminConfirmAttendance(daily.id, userId)
      toast.success('Jogador confirmado!')
      fetchDaily()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao confirmar jogador')
    } finally {
      setAdminToggleLoading(null)
    }
  }

  const handleAdminDisconfirm = async (userId: number) => {
    if (!daily) return
    setAdminToggleLoading(userId)
    try {
      await adminDisconfirmAttendance(daily.id, userId)
      toast.success('Jogador removido.')
      fetchDaily()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao remover jogador')
    } finally {
      setAdminToggleLoading(null)
    }
  }

  const handleTeamNameChange = async (teamId: number, name: string) => {
    if (!daily) return
    try {
      const updated = await updateTeamName(daily.id, teamId, name)
      setDaily((prev) => prev ? { ...prev, teams: prev.teams.map((t) => t.id === teamId ? updated : t) } : prev)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao renomear time')
    }
  }

  const handleTeamColorChange = async (teamId: number, color: string) => {
    if (!daily) return
    try {
      const updated = await updateTeamColor(daily.id, teamId, color)
      setDaily((prev) => prev ? { ...prev, teams: prev.teams.map((t) => t.id === teamId ? updated : t) } : prev)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Falha ao alterar cor do time')
    }
  }

  const isCurrentUserConfirmed =
    user != null && daily != null && daily.confirmedPlayers.some((p) => p.id === user.id)

  const canToggleAttendance =
    daily != null &&
    (daily.status === 'SCHEDULED' || daily.status === 'CONFIRMED')

  return (
    <div className="page-enter min-h-screen flex flex-col">
      <NavBar />
      {loading ? (
        <DetailSkeleton />
      ) : accessDenied ? (
        <div className="flex items-center justify-center py-24">
          <div className="text-center">
            <h2 className="text-2xl font-bold mb-2">Acesso Negado</h2>
            <p className="text-muted-foreground">Você não faz parte dessa pelada.</p>
          </div>
        </div>
      ) : daily == null ? null : (
        <main className={`container max-w-4xl mx-auto px-4 py-6${daily.isAdmin ? ' pb-24' : ''}`}>
          {/* Header */}
          <div className="mb-6 flex items-start justify-between gap-4">
            <div className="min-w-0">
              <Link
                to={`/pelada/${daily.peladaId}`}
                className="text-sm text-muted-foreground hover:underline"
              >
                ← {daily.peladaName}
              </Link>
              <div className="flex items-center gap-3 mt-1 flex-wrap">
                <h1 className="text-2xl font-bold tracking-tight">
                  {formattedDate}
                </h1>
                <Badge
                  className={{
                    SCHEDULED: 'border-transparent bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
                    CONFIRMED: 'border-transparent bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
                    IN_COURSE: 'border-transparent bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
                    FINISHED: 'border-transparent bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300',
                    CANCELED: 'border-transparent bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
                  }[daily.status] ?? 'border-transparent bg-gray-100 text-gray-800'}
                >
                  {daily.status.replace('_', ' ')}
                </Badge>
                {daily.status === 'IN_COURSE' && (
                  <span className="flex items-center gap-1.5 text-sm font-medium text-green-600 dark:text-green-400">
                    <span className="animate-pulse bg-green-500 rounded-full w-2 h-2 inline-block" />
                    Ao vivo
                  </span>
                )}
              </div>
              <p className="text-sm text-muted-foreground mt-1">{daily.dailyTime}</p>
            </div>

            {/* Attendance action */}
            {canToggleAttendance && (
              <div className="flex flex-col items-center sm:items-end gap-1 shrink-0 mt-4">
                <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">Presença</span>
                {isCurrentUserConfirmed ? (
                  <Button
                    variant="outline"
                    className="text-destructive sm:rounded-full rounded-full sm:w-auto sm:h-auto w-10 h-10 p-0 sm:px-4 sm:py-2"
                    disabled={confirmLoading}
                    onClick={handleDisconfirm}
                  >
                    <CalendarX className="h-4 w-4 text-destructive" />
                    <span className="hidden sm:inline">{confirmLoading ? 'Atualizando...' : 'Cancelar Presença'}</span>
                  </Button>
                ) : (
                  <Button
                    variant="gradient"
                    className="rounded-full sm:w-auto sm:h-auto w-10 h-10 p-0 sm:px-4 sm:py-2"
                    disabled={confirmLoading}
                    onClick={handleConfirm}
                  >
                    <CalendarCheck className="h-4 w-4" />
                    <span className="hidden sm:inline">{confirmLoading ? 'Atualizando...' : 'Confirmar Presença'}</span>
                  </Button>
                )}
              </div>
            )}
          </div>

          {/* Admin action bar */}
          <AdminActionBar
            daily={daily}
            onConfirmDaily={() =>
              setStatusDialog({
                targetStatus: 'CONFIRMED',
                description: 'Isso vai marcar diária como confirmado, e isso vai bloquear outras ações.',
              })
            }
            onStartSession={() =>
              setStatusDialog({
                targetStatus: 'IN_COURSE',
                description: 'Isso vai marcar sessão como durante. Essa diária está agora ao vivo.',
              })
            }
            onCancelDaily={() =>
              setStatusDialog({
                targetStatus: 'CANCELED',
                description: 'Isso vai cancelar a sessão atual. Essa ação nao pode ser desfeita.',
              })
            }
            onEnterResults={() => setResultsOpen(true)}
            onFinalizeDaily={() => setFinalizeOpen(true)}
            onImportFromMessage={() => setImportOpen(true)}
          />

          {/* Results modal */}
          {resultsOpen && (
            <ResultsModal
              daily={daily}
              onClose={() => setResultsOpen(false)}
              onSuccess={(updated) => {
                setDaily(updated)
                setResultsOpen(false)
              }}
            />
          )}

          {/* Finalize modal */}
          {finalizeOpen && (
            <FinalizeModal
              daily={daily}
              onClose={() => setFinalizeOpen(false)}
              onSuccess={(updated) => {
                setDaily(updated)
                setFinalizeOpen(false)
              }}
            />
          )}

          {/* Import from message modal */}
          {importOpen && daily && (
            <ImportFromMessageModal
              daily={daily}
              onClose={() => setImportOpen(false)}
              onSuccess={(updated) => { setDaily(updated); setImportOpen(false) }}
            />
          )}

          {/* Confirmation dialog */}
          {statusDialog && (
            <StatusConfirmDialog
              description={statusDialog.description}
              loading={statusLoading}
              onConfirm={handleStatusConfirm}
              onClose={() => setStatusDialog(null)}
            />
          )}

          {/* Confirmed Players */}
          {daily.status !== 'FINISHED' && (
            <ConfirmedPlayersSection
              daily={daily}
              adminToggleLoading={adminToggleLoading}
              onAdminConfirm={handleAdminConfirm}
              onAdminDisconfirm={handleAdminDisconfirm}
            />
          )}

          {/* Teams (non-FINISHED) */}
          {daily.status !== 'FINISHED' && (
            <TeamsSection
              daily={daily}
              sortLoading={sortLoading}
              swapLoading={swapLoading}
              selectedPlayer={selectedPlayer}
              onSortTeams={handleSortTeams}
              onPlayerClick={handlePlayerClick}
              currentUserId={user?.id ?? null}
              onTeamNameChange={handleTeamNameChange}
              onTeamColorChange={handleTeamColorChange}
            />
          )}

          {/* IN_COURSE CTA card */}
          <LiveSessionCard daily={daily} onEnterResults={() => setResultsOpen(true)} />

          {/* Live results during IN_COURSE */}
          {daily.status === 'IN_COURSE' && (
            <>
              <LeagueTableSection daily={daily} />
              <MatchResultsSection daily={daily} />
            </>
          )}

          {/* Finished sections */}
          {daily.status === 'FINISHED' && (
            <>
              <ChampionPhotoSection
                daily={daily}
                fileInputRef={fileInputRef}
                uploadLoading={uploadLoading}
                onUploadClick={() => fileInputRef.current?.click()}
                onChange={handleChampionUpload}
              />
              <AwardsSection daily={daily} />
              <LeagueTableSection daily={daily} />
              <MatchResultsSection daily={daily} />
              <PlayerStatsSection stats={daily.playerStats} />
              <TeamsSection
                daily={daily}
                sortLoading={sortLoading}
                swapLoading={swapLoading}
                selectedPlayer={selectedPlayer}
                onSortTeams={handleSortTeams}
                onPlayerClick={handlePlayerClick}
                currentUserId={user?.id ?? null}
                onTeamNameChange={handleTeamNameChange}
                onTeamColorChange={handleTeamColorChange}
              />
            </>
          )}
        </main>
      )}
    </div>
  )
}
