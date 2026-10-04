import { useParams } from 'react-router-dom'
import ImportFromMessageModal from '@/components/daily/ImportFromMessageModal'
import { useAuth } from '@/hooks/useAuth'
import { Input } from '@/components/ui/input'
import DetailSkeleton from '@/components/daily/DetailSkeleton'
import TeamsSection from '@/components/daily/TeamsSection'
import ResultsModal from '@/components/daily/ResultsModal'
import FinalizeModal from '@/components/daily/FinalizeModal'
import DeleteDailyDialog from '@/components/daily/DeleteDailyDialog'
import StatusConfirmDialog from '@/components/daily/StatusConfirmDialog'
import LiveSessionCard from '@/components/daily/LiveSessionCard'
import MatchResultsSection from '@/components/daily/MatchResultsSection'
import LeagueTableSection from '@/components/daily/LeagueTableSection'
import PlayerStatsSection from '@/components/daily/PlayerStatsSection'
import AwardsSection from '@/components/daily/AwardsSection'
import ChampionPhotoSection from '@/components/daily/ChampionPhotoSection'
import { DailyHeader } from '@/components/daily/DailyHeader'
import { AttendanceSummaryCard } from '@/components/daily/AttendanceSummaryCard'
import { AttendanceList } from '@/components/daily/AttendanceList'
import { useDailyDetail } from '@/components/daily/hooks/useDailyDetail'
import { useDailyModals } from '@/components/daily/hooks/useDailyModals'
import { useDailyActions } from '@/components/daily/hooks/useDailyActions'
import { IMAGE_ACCEPT } from '@/schemas/upload'
import { isDailyOpen } from '@/types/daily'

export default function DailyDetailPage() {
  const { id } = useParams<{ id: string }>()
  const dailyId = Number(id)
  const { user } = useAuth()

  const { daily, setDaily, loading, accessDenied, refetch } = useDailyDetail(dailyId)

  const {
    resultsOpen, openResults, closeResults,
    finalizeOpen, openFinalize, closeFinalize,
    importOpen, openImport, closeImport,
    statusDialog, setStatusDialog,
    deleteOpen, setDeleteOpen,
    attendanceOpen, setAttendanceOpen,
    fileInputRef,
  } = useDailyModals()

  const actions = useDailyActions({ daily, setDaily, refetch })

  async function handleStatusConfirm() {
    if (!statusDialog) return
    if (await actions.changeStatus(statusDialog.targetStatus)) setStatusDialog(null)
  }

  async function handleSortTeams() {
    // the attendance list collapses once the teams are drawn
    if (await actions.handleSortTeams()) setAttendanceOpen(false)
  }

  function handleChampionUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) void actions.uploadChampion(file)
    e.target.value = ''
  }

  if (loading) return <DetailSkeleton />

  if (accessDenied) {
    return (
      <div className="flex items-center justify-center py-24 text-center">
        <div>
          <h2 className="mb-2 text-2xl font-bold">Acesso negado</h2>
          <p className="text-muted-foreground">Você não faz parte desta pelada.</p>
        </div>
      </div>
    )
  }

  if (daily == null) return null

  const isCurrentUserConfirmed = user != null && daily.confirmedPlayers.some((p) => p.id === user.id)
  const open = isDailyOpen(daily.status)
  const preStart = open || daily.status === 'CANCELED'

  return (
    <div className="page-enter flex flex-1 flex-col">
      <main className="mx-auto flex w-full max-w-[1120px] flex-col gap-7 p-4 md:px-8 md:py-7">
        <DailyHeader
          daily={daily}
          onImportFromMessage={openImport}
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
          onChangeChampionPhoto={() => fileInputRef.current?.click()}
          onDeleteDaily={() => setDeleteOpen(true)}
        />

        {preStart && (
          <>
            {open && (
              <AttendanceSummaryCard
                daily={daily}
                isConfirmed={isCurrentUserConfirmed}
                pending={actions.confirmLoading}
                onToggle={() => actions.toggleAttendance(isCurrentUserConfirmed)}
              />
            )}
            <AttendanceList
              daily={daily}
              open={attendanceOpen ?? daily.teams.length === 0}
              onOpenChange={setAttendanceOpen}
              canManage={daily.isAdmin && open}
              togglingId={actions.adminToggleLoading}
              confirmAllPending={actions.confirmAllLoading}
              onConfirm={(userId) => actions.adminToggle(userId, true)}
              onUnconfirm={(userId) => actions.adminToggle(userId, false)}
              onConfirmAll={actions.confirmAll}
            />
            <TeamsSection
              daily={daily}
              sortLoading={actions.sortLoading}
              swapLoading={actions.swapLoading}
              selectedPlayer={actions.selectedPlayer}
              onSortTeams={handleSortTeams}
              onPlayerClick={actions.handlePlayerClick}
              currentUserId={user?.id ?? null}
              onTeamNameChange={actions.changeTeamName}
              onTeamColorChange={actions.changeTeamColor}
            />
          </>
        )}

        {daily.status === 'IN_COURSE' && (
          <>
            <LiveSessionCard daily={daily} onEnterResults={openResults} />
            <LeagueTableSection daily={daily} />
            <MatchResultsSection daily={daily} />
          </>
        )}

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
          </>
        )}
      </main>

      {/* Champion photo picker, opened from the header menu */}
      {daily.status === 'FINISHED' && daily.isAdmin && (
        <Input ref={fileInputRef} type="file" accept={IMAGE_ACCEPT} className="hidden" onChange={handleChampionUpload} />
      )}

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

      {importOpen && (
        <ImportFromMessageModal
          daily={daily}
          onClose={closeImport}
          onSuccess={(updated) => {
            setDaily(updated)
            closeImport()
          }}
        />
      )}

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

      {daily.isAdmin && (
        <DeleteDailyDialog
          open={deleteOpen}
          onOpenChange={setDeleteOpen}
          dailyId={daily.id}
          peladaId={daily.peladaId}
          dailyDate={daily.dailyDate}
        />
      )}
    </div>
  )
}
