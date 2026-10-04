import { CalendarCheck, CalendarX } from 'lucide-react'
import { useParams, Link } from 'react-router-dom'
import NavBar from '@/components/NavBar'
import ImportFromMessageModal from '@/components/daily/ImportFromMessageModal'
import { useAuth } from '@/hooks/useAuth'
import { Button } from '@/components/ui/button'
import { DailyStatusBadge } from '@/components/daily/DailyStatusBadge'
import DetailSkeleton from '@/components/daily/DetailSkeleton'
import TeamsSection from '@/components/daily/TeamsSection'
import ResultsModal from '@/components/daily/ResultsModal'
import FinalizeModal from '@/components/daily/FinalizeModal'
import ConfirmedPlayersSection from '@/components/daily/ConfirmedPlayersSection'
import AdminActionBar from '@/components/daily/AdminActionBar'
import StatusConfirmDialog from '@/components/daily/StatusConfirmDialog'
import LiveSessionCard from '@/components/daily/LiveSessionCard'
import MatchResultsSection from '@/components/daily/MatchResultsSection'
import LeagueTableSection from '@/components/daily/LeagueTableSection'
import PlayerStatsSection from '@/components/daily/PlayerStatsSection'
import AwardsSection from '@/components/daily/AwardsSection'
import ChampionPhotoSection from '@/components/daily/ChampionPhotoSection'
import { useDailyDetail } from '@/components/daily/hooks/useDailyDetail'
import { useDailyModals } from '@/components/daily/hooks/useDailyModals'
import { useDailyActions } from '@/components/daily/hooks/useDailyActions'
import { isDailyOpen } from '@/types/daily'

export default function DailyDetailPage() {
  const { id } = useParams<{ id: string }>()
  const dailyId = Number(id)
  const { user } = useAuth()

  const { daily, setDaily, loading, accessDenied, formattedDate, refetch } = useDailyDetail(dailyId)

  const {
    resultsOpen, openResults, closeResults,
    finalizeOpen, openFinalize, closeFinalize,
    importOpen, openImport, closeImport,
    statusDialog, setStatusDialog,
    fileInputRef,
  } = useDailyModals()

  const actions = useDailyActions({ daily, setDaily, refetch })

  async function handleStatusConfirm() {
    if (!statusDialog) return
    if (await actions.changeStatus(statusDialog.targetStatus)) setStatusDialog(null)
  }

  function handleChampionUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) void actions.uploadChampion(file)
    e.target.value = ''
  }

  const isCurrentUserConfirmed =
    user != null && daily != null && daily.confirmedPlayers.some((p) => p.id === user.id)

  const canToggleAttendance = daily != null && isDailyOpen(daily.status)

  return (
    <div className="page-enter min-h-screen flex flex-col">
      <NavBar />
      {loading ? (
        <DetailSkeleton />
      ) : accessDenied ? (
        <div className="flex items-center justify-center py-24">
          <div className="text-center">
            <h2 className="text-2xl font-bold mb-2">Acesso negado</h2>
            <p className="text-muted-foreground">Você não faz parte desta pelada.</p>
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
                <DailyStatusBadge status={daily.status} />
                {daily.status === 'IN_COURSE' && (
                  <span className="flex items-center gap-1.5 text-sm font-medium text-chart-1">
                    <span className="inline-block size-2 animate-pulse rounded-full bg-chart-1" />
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
                    disabled={actions.confirmLoading}
                    onClick={() => actions.toggleAttendance(true)}
                  >
                    <CalendarX className="h-4 w-4 text-destructive" />
                    <span className="hidden sm:inline">{actions.confirmLoading ? 'Atualizando...' : 'Cancelar presença'}</span>
                  </Button>
                ) : (
                  <Button
                    variant="gradient"
                    className="rounded-full sm:w-auto sm:h-auto w-10 h-10 p-0 sm:px-4 sm:py-2"
                    disabled={actions.confirmLoading}
                    onClick={() => actions.toggleAttendance(false)}
                  >
                    <CalendarCheck className="h-4 w-4" />
                    <span className="hidden sm:inline">{actions.confirmLoading ? 'Atualizando...' : 'Confirmar presença'}</span>
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
                title: 'Confirmar sessão',
                description: 'A sessão passa a confirmada. A lista de presença continua aberta até a sessão começar.',
                variant: 'gradient',
              })
            }
            onStartSession={() =>
              setStatusDialog({
                targetStatus: 'IN_COURSE',
                title: 'Iniciar sessão',
                description: 'A sessão fica ao vivo e a presença e os times não podem mais mudar.',
              })
            }
            onCancelDaily={() =>
              setStatusDialog({
                targetStatus: 'CANCELED',
                title: 'Cancelar sessão',
                description: 'A sessão será cancelada. Essa ação não pode ser desfeita.',
              })
            }
            onEnterResults={openResults}
            onFinalizeDaily={openFinalize}
            onImportFromMessage={openImport}
          />

          {/* Results modal */}
          {resultsOpen && (
            <ResultsModal
              daily={daily}
              onClose={closeResults}
              onSuccess={(updated) => {
                setDaily(updated)
                closeResults()
              }}
            />
          )}

          {/* Finalize modal */}
          {finalizeOpen && (
            <FinalizeModal
              daily={daily}
              onClose={closeFinalize}
              onSuccess={(updated) => {
                setDaily(updated)
                closeFinalize()
              }}
            />
          )}

          {/* Import from message modal */}
          {importOpen && daily && (
            <ImportFromMessageModal
              daily={daily}
              onClose={closeImport}
              onSuccess={(updated) => { setDaily(updated); closeImport() }}
            />
          )}

          {/* Confirmation dialog */}
          {statusDialog && (
            <StatusConfirmDialog
              title={statusDialog.title}
              description={statusDialog.description}
              variant={statusDialog.variant}
              loading={actions.statusLoading}
              onConfirm={handleStatusConfirm}
              onClose={() => setStatusDialog(null)}
            />
          )}

          {/* Confirmed Players */}
          {daily.status !== 'FINISHED' && (
            <ConfirmedPlayersSection
              daily={daily}
              adminToggleLoading={actions.adminToggleLoading}
              onAdminConfirm={(userId) => actions.adminToggle(userId, true)}
              onAdminDisconfirm={(userId) => actions.adminToggle(userId, false)}
            />
          )}

          {/* Teams (non-FINISHED) */}
          {daily.status !== 'FINISHED' && (
            <TeamsSection
              daily={daily}
              sortLoading={actions.sortLoading}
              swapLoading={actions.swapLoading}
              selectedPlayer={actions.selectedPlayer}
              onSortTeams={actions.handleSortTeams}
              onPlayerClick={actions.handlePlayerClick}
              currentUserId={user?.id ?? null}
              onTeamNameChange={actions.changeTeamName}
              onTeamColorChange={actions.changeTeamColor}
            />
          )}

          {/* IN_COURSE CTA card */}
          <LiveSessionCard daily={daily} onEnterResults={openResults} />

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
                uploadLoading={actions.uploadLoading}
                onUploadClick={() => fileInputRef.current?.click()}
                onChange={handleChampionUpload}
              />
              <AwardsSection daily={daily} />
              <LeagueTableSection daily={daily} />
              <MatchResultsSection daily={daily} />
              <PlayerStatsSection stats={daily.playerStats} />
              <TeamsSection
                daily={daily}
                sortLoading={actions.sortLoading}
                swapLoading={actions.swapLoading}
                selectedPlayer={actions.selectedPlayer}
                onSortTeams={actions.handleSortTeams}
                onPlayerClick={actions.handlePlayerClick}
                currentUserId={user?.id ?? null}
                onTeamNameChange={actions.changeTeamName}
                onTeamColorChange={actions.changeTeamColor}
              />
            </>
          )}
        </main>
      )}
    </div>
  )
}
