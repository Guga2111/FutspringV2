import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { toast } from 'sonner'
import NavBar from '../components/NavBar'
import { getDailyDetail, confirmAttendance, disconfirmAttendance, sortTeams, swapPlayers, updateDailyStatus, submitResults, finalizeDaily, uploadChampionImage } from '../api/dailies'
import type { MatchResultInput } from '../api/dailies'
import type { DailyDetail, PlayerDTO, TeamDTO } from '../types/daily'
import { useAuth } from '../hooks/useAuth'
import { Button } from '../components/ui/button'
import { Badge } from '../components/ui/badge'

function SkeletonBlock({ className }: { className: string }) {
  return <div className={`bg-muted rounded animate-pulse ${className}`} />
}

function StatusBadge({ status }: { status: string }) {
  const colorMap: Record<string, string> = {
    SCHEDULED: 'bg-blue-100 text-blue-800',
    CONFIRMED: 'bg-yellow-100 text-yellow-800',
    IN_COURSE: 'bg-green-100 text-green-800',
    FINISHED: 'bg-gray-100 text-gray-800',
    CANCELED: 'bg-red-100 text-red-800',
  }
  const color = colorMap[status] ?? 'bg-gray-100 text-gray-800'
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${color}`}>
      {status.replace('_', ' ')}
    </span>
  )
}

function PlayerAvatar({ player }: { player: PlayerDTO }) {
  if (player.image) {
    return (
      <img
        src={`/api/v1/files/${player.image}`}
        alt={player.username}
        className="h-10 w-10 rounded-full object-cover flex-shrink-0"
      />
    )
  }
  const initials = player.username.slice(0, 2).toUpperCase()
  return (
    <div className="h-10 w-10 rounded-full bg-muted flex items-center justify-center flex-shrink-0 text-sm font-semibold">
      {initials}
    </div>
  )
}

function DetailSkeleton() {
  return (
    <div className="container max-w-4xl mx-auto px-4 py-6">
      <SkeletonBlock className="h-6 w-1/3 mb-2" />
      <SkeletonBlock className="h-8 w-1/2 mb-3" />
      <SkeletonBlock className="h-5 w-24 mb-6" />
      <SkeletonBlock className="h-6 w-40 mb-4" />
      {[0, 1, 2].map((i) => (
        <div key={i} className="flex items-center gap-3 mb-3">
          <SkeletonBlock className="h-10 w-10 rounded-full flex-shrink-0" />
          <SkeletonBlock className="h-4 w-32" />
        </div>
      ))}
    </div>
  )
}

function StarRating({ stars }: { stars: number }) {
  return (
    <span className="text-yellow-400 text-xs">
      {Array.from({ length: 5 }, (_, i) => (
        <span key={i}>{i < stars ? '★' : '☆'}</span>
      ))}
    </span>
  )
}

interface TeamsSectionProps {
  daily: DailyDetail
  sortLoading: boolean
  swapLoading: boolean
  selectedPlayer: { id: number; teamId: number } | null
  onSortTeams: () => void
  onPlayerClick: (playerId: number, teamId: number) => void
}

function TeamsSection({
  daily,
  sortLoading,
  swapLoading,
  selectedPlayer,
  onSortTeams,
  onPlayerClick,
}: TeamsSectionProps) {
  const canSort =
    daily.isAdmin &&
    (daily.status === 'SCHEDULED' || daily.status === 'CONFIRMED') &&
    daily.confirmedPlayers.length >= 2

  const hasTeams = daily.teams.length > 0

  if (!canSort && !hasTeams) return null

  return (
    <section className="mb-8">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-lg font-semibold">Teams</h2>
        {canSort && (
          <Button
            variant="gradient"
            size="sm"
            disabled={sortLoading || swapLoading}
            onClick={onSortTeams}
          >
            {sortLoading ? 'Sorting...' : hasTeams ? 'Re-sort Teams' : 'Sort Teams'}
          </Button>
        )}
      </div>
      {selectedPlayer !== null && (
        <p className="text-sm text-muted-foreground mb-3">
          Player selected — click a player in a different team to swap, or click the same player to cancel.
        </p>
      )}
      {hasTeams ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {daily.teams.map((team: TeamDTO) => (
            <div key={team.id} className="border rounded-lg p-4">
              <div className="flex items-center justify-between mb-2">
                <h3 className="font-semibold">{team.name}</h3>
                <StarRating stars={team.totalStars} />
              </div>
              <div className="space-y-1">
                {team.players.map((player: PlayerDTO) => {
                  const isSelected = selectedPlayer?.id === player.id
                  return (
                    <div
                      key={player.id}
                      className={`flex items-center gap-2 p-1.5 rounded ${isSelected ? 'bg-primary/10 ring-1 ring-primary' : 'hover:bg-muted'}`}
                    >
                      <PlayerAvatar player={player} />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium truncate">{player.username}</p>
                      </div>
                      <StarRating stars={player.stars} />
                      {daily.isAdmin && (
                        <button
                          className={`text-xs px-2 py-0.5 rounded border ${isSelected ? 'bg-primary text-primary-foreground border-primary' : 'border-muted-foreground text-muted-foreground hover:bg-muted'} disabled:opacity-50`}
                          disabled={swapLoading}
                          onClick={() => onPlayerClick(player.id, team.id)}
                        >
                          {isSelected ? 'Cancel' : 'Move'}
                        </button>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">No teams yet. Use "Sort Teams" to auto-balance.</p>
      )}
    </section>
  )
}

// ── Results Modal ────────────────────────────────────────────────────────────

interface MatchFormRow {
  matchId: number | null
  team1Id: number
  team2Id: number
  team1Score: string
  team2Score: string
  statsExpanded: boolean
  playerStats: { userId: number; username: string; goals: string; assists: string }[]
}

function buildDefaultPlayerStats(daily: DailyDetail) {
  return daily.confirmedPlayers.map((p) => ({
    userId: p.id,
    username: p.username,
    goals: '0',
    assists: '0',
  }))
}

function initMatchRows(daily: DailyDetail): MatchFormRow[] {
  const defaultStats = buildDefaultPlayerStats(daily)
  if (daily.matches.length > 0) {
    return daily.matches.map((m) => ({
      matchId: m.id,
      team1Id: m.team1Id,
      team2Id: m.team2Id,
      team1Score: String(m.team1Score ?? 0),
      team2Score: String(m.team2Score ?? 0),
      statsExpanded: false,
      playerStats: defaultStats,
    }))
  }
  if (daily.teams.length >= 2) {
    return [
      {
        matchId: null,
        team1Id: daily.teams[0].id,
        team2Id: daily.teams[1].id,
        team1Score: '0',
        team2Score: '0',
        statsExpanded: false,
        playerStats: defaultStats,
      },
    ]
  }
  return []
}

interface ResultsModalProps {
  daily: DailyDetail
  onClose: () => void
  onSuccess: (updated: DailyDetail) => void
}

function ResultsModal({ daily, onClose, onSuccess }: ResultsModalProps) {
  const [rows, setRows] = useState<MatchFormRow[]>(() => initMatchRows(daily))
  const [loading, setLoading] = useState(false)

  function updateRow(index: number, patch: Partial<MatchFormRow>) {
    setRows((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)))
  }

  function updatePlayerStat(
    matchIndex: number,
    playerIndex: number,
    field: 'goals' | 'assists',
    value: string,
  ) {
    setRows((prev) =>
      prev.map((r, i) => {
        if (i !== matchIndex) return r
        const updatedStats = r.playerStats.map((ps, pi) =>
          pi === playerIndex ? { ...ps, [field]: value } : ps,
        )
        return { ...r, playerStats: updatedStats }
      }),
    )
  }

  function addMatch() {
    setRows((prev) => [
      ...prev,
      {
        matchId: null,
        team1Id: daily.teams[0]?.id ?? 0,
        team2Id: daily.teams[1]?.id ?? 0,
        team1Score: '0',
        team2Score: '0',
        statsExpanded: false,
        playerStats: buildDefaultPlayerStats(daily),
      },
    ])
  }

  function removeMatch(index: number) {
    setRows((prev) => prev.filter((_, i) => i !== index))
  }

  async function handleSubmit() {
    setLoading(true)
    try {
      const payload: MatchResultInput[] = rows.map((r) => ({
        matchId: r.matchId ?? null,
        team1Id: r.team1Id,
        team2Id: r.team2Id,
        team1Score: Math.max(0, parseInt(r.team1Score) || 0),
        team2Score: Math.max(0, parseInt(r.team2Score) || 0),
        playerStats: r.playerStats.map((ps) => ({
          userId: ps.userId,
          goals: Math.max(0, parseInt(ps.goals) || 0),
          assists: Math.max(0, parseInt(ps.assists) || 0),
        })),
      }))
      await submitResults(daily.id, payload)
      // Re-fetch fresh detail to get updated matches
      const { getDailyDetail } = await import('../api/dailies')
      const updated = await getDailyDetail(daily.id)
      toast.success('Results saved!')
      onSuccess(updated)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to save results')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/50 overflow-y-auto py-8 px-4">
      <div className="bg-background rounded-lg shadow-lg w-full max-w-2xl">
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-lg font-semibold">Enter Match Results</h2>
          <button
            className="text-muted-foreground hover:text-foreground text-xl leading-none"
            onClick={onClose}
          >
            ×
          </button>
        </div>
        <div className="p-4 space-y-6">
          {rows.length === 0 && (
            <p className="text-sm text-muted-foreground">
              No teams available. Sort teams before entering results.
            </p>
          )}
          {rows.map((row, mi) => {
            const team1Name =
              daily.teams.find((t) => t.id === row.team1Id)?.name ?? `Team ${row.team1Id}`
            const team2Name =
              daily.teams.find((t) => t.id === row.team2Id)?.name ?? `Team ${row.team2Id}`
            return (
              <div key={mi} className="border rounded-lg p-4">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="font-medium text-sm">Match {mi + 1}</h3>
                  {rows.length > 1 && (
                    <button
                      className="text-xs text-destructive hover:underline"
                      onClick={() => removeMatch(mi)}
                    >
                      Remove
                    </button>
                  )}
                </div>

                {/* Team selects */}
                <div className="grid grid-cols-2 gap-3 mb-1">
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">Team 1</label>
                    <select
                      className="w-full text-sm border rounded px-2 py-1.5 bg-background"
                      value={row.team1Id}
                      onChange={(e) => updateRow(mi, { team1Id: Number(e.target.value) })}
                    >
                      {daily.teams.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">Team 2</label>
                    <select
                      className="w-full text-sm border rounded px-2 py-1.5 bg-background"
                      value={row.team2Id}
                      onChange={(e) => updateRow(mi, { team2Id: Number(e.target.value) })}
                    >
                      {daily.teams.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {/* Score inputs */}
                <div className="grid grid-cols-2 gap-3 mb-3">
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">
                      {team1Name} score
                    </label>
                    <input
                      type="number"
                      min="0"
                      className="w-full text-sm border rounded px-2 py-1.5 bg-background"
                      value={row.team1Score}
                      onChange={(e) => updateRow(mi, { team1Score: e.target.value })}
                    />
                  </div>
                  <div>
                    <label className="text-xs text-muted-foreground mb-1 block">
                      {team2Name} score
                    </label>
                    <input
                      type="number"
                      min="0"
                      className="w-full text-sm border rounded px-2 py-1.5 bg-background"
                      value={row.team2Score}
                      onChange={(e) => updateRow(mi, { team2Score: e.target.value })}
                    />
                  </div>
                </div>

                {/* Player stats toggle */}
                <button
                  className="text-xs text-muted-foreground hover:text-foreground mb-2"
                  onClick={() => updateRow(mi, { statsExpanded: !row.statsExpanded })}
                >
                  {row.statsExpanded ? '▾ Hide' : '▸ Show'} player stats (
                  {row.playerStats.length} players)
                </button>

                {row.statsExpanded && (
                  <div className="space-y-1">
                    <div className="grid grid-cols-[1fr_5rem_5rem] gap-2 text-xs text-muted-foreground px-1 mb-1">
                      <span>Player</span>
                      <span className="text-center">Goals</span>
                      <span className="text-center">Assists</span>
                    </div>
                    {row.playerStats.map((ps, pi) => (
                      <div
                        key={ps.userId}
                        className="grid grid-cols-[1fr_5rem_5rem] gap-2 items-center"
                      >
                        <span className="text-sm truncate">{ps.username}</span>
                        <input
                          type="number"
                          min="0"
                          className="text-sm border rounded px-2 py-1 bg-background text-center w-full"
                          value={ps.goals}
                          onChange={(e) => updatePlayerStat(mi, pi, 'goals', e.target.value)}
                        />
                        <input
                          type="number"
                          min="0"
                          className="text-sm border rounded px-2 py-1 bg-background text-center w-full"
                          value={ps.assists}
                          onChange={(e) => updatePlayerStat(mi, pi, 'assists', e.target.value)}
                        />
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )
          })}

          {daily.teams.length >= 2 && (
            <button
              className="text-sm text-muted-foreground border border-dashed rounded px-3 py-2 w-full hover:bg-muted"
              onClick={addMatch}
            >
              + Add Match
            </button>
          )}
        </div>

        <div className="flex justify-end gap-3 p-4 border-t">
          <button
            className="text-sm px-4 py-2 rounded border hover:bg-muted disabled:opacity-50"
            disabled={loading}
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            className="text-sm bg-primary text-primary-foreground px-4 py-2 rounded disabled:opacity-50"
            disabled={loading || rows.length === 0}
            onClick={handleSubmit}
          >
            {loading ? 'Saving...' : 'Save Results'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Finalize Modal ────────────────────────────────────────────────────────────

interface FinalizeModalProps {
  daily: DailyDetail
  onClose: () => void
  onSuccess: (updated: DailyDetail) => void
}

function FinalizeModal({ daily, onClose, onSuccess }: FinalizeModalProps) {
  const [puskasWinnerId, setPuskasWinnerId] = useState<string>('')
  const [wiltballWinnerId, setWiltballWinnerId] = useState<string>('')
  const [loading, setLoading] = useState(false)

  const canSubmit = puskasWinnerId !== '' && wiltballWinnerId !== ''

  async function handleSubmit() {
    if (!canSubmit) return
    setLoading(true)
    try {
      const updated = await finalizeDaily(daily.id, Number(puskasWinnerId), Number(wiltballWinnerId))
      toast.success('Daily finalized!')
      onSuccess(updated)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to finalize daily')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
      <div className="bg-background rounded-lg shadow-lg w-full max-w-md">
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-lg font-semibold">Finalize Daily</h2>
          <button
            className="text-muted-foreground hover:text-foreground text-xl leading-none"
            onClick={onClose}
          >
            ×
          </button>
        </div>
        <div className="p-4 space-y-4">
          <p className="text-sm text-muted-foreground">
            Select the award winners to finalize the session and compute stats.
          </p>
          <div>
            <label className="text-sm font-medium block mb-1">Puskas Award</label>
            <select
              className="w-full text-sm border rounded px-2 py-1.5 bg-background"
              value={puskasWinnerId}
              onChange={(e) => setPuskasWinnerId(e.target.value)}
            >
              <option value="">— Select player —</option>
              {daily.confirmedPlayers.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.username}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="text-sm font-medium block mb-1">Wiltball Award</label>
            <select
              className="w-full text-sm border rounded px-2 py-1.5 bg-background"
              value={wiltballWinnerId}
              onChange={(e) => setWiltballWinnerId(e.target.value)}
            >
              <option value="">— Select player —</option>
              {daily.confirmedPlayers.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.username}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="flex justify-end gap-3 p-4 border-t">
          <button
            className="text-sm px-4 py-2 rounded border hover:bg-muted disabled:opacity-50"
            disabled={loading}
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            className="text-sm bg-primary text-primary-foreground px-4 py-2 rounded disabled:opacity-50"
            disabled={loading || !canSubmit}
            onClick={handleSubmit}
          >
            {loading ? 'Finalizing...' : 'Finalize'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────

export default function DailyDetailPage() {
  const { id } = useParams<{ id: string }>()
  const dailyId = Number(id)
  const { user } = useAuth()

  const [daily, setDaily] = useState<DailyDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)
  const [confirmLoading, setConfirmLoading] = useState(false)
  const [sortLoading, setSortLoading] = useState(false)
  const [selectedPlayer, setSelectedPlayer] = useState<{ id: number; teamId: number } | null>(null)
  const [swapLoading, setSwapLoading] = useState(false)
  const [statusDialog, setStatusDialog] = useState<{ targetStatus: string; description: string } | null>(null)
  const [statusLoading, setStatusLoading] = useState(false)
  const [resultsOpen, setResultsOpen] = useState(false)
  const [finalizeOpen, setFinalizeOpen] = useState(false)
  const [uploadLoading, setUploadLoading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const fetchDaily = useCallback(async () => {
    try {
      const data = await getDailyDetail(dailyId)
      setDaily(data)
    } catch (err: unknown) {
      const e = err as { response?: { status?: number } }
      if (e?.response?.status === 403) {
        setAccessDenied(true)
      } else {
        toast.error('Failed to load daily session')
      }
    } finally {
      setLoading(false)
    }
  }, [dailyId])

  useEffect(() => {
    fetchDaily()
  }, [fetchDaily])

  const handleConfirm = async () => {
    if (!daily) return
    setConfirmLoading(true)
    try {
      await confirmAttendance(daily.id)
      toast.success('Attendance confirmed!')
      fetchDaily()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to confirm attendance')
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
      toast.success('Teams sorted!')
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to sort teams')
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
    // Cancel selection if same player clicked
    if (selectedPlayer.id === playerId) {
      setSelectedPlayer(null)
      return
    }
    // Must be a different team to swap
    if (selectedPlayer.teamId === teamId) {
      toast.error('Select a player from a different team to swap')
      return
    }
    setSwapLoading(true)
    try {
      await swapPlayers(daily.id, selectedPlayer.id, playerId)
      await fetchDaily()
      setSelectedPlayer(null)
      toast.success('Players swapped!')
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to swap players')
    } finally {
      setSwapLoading(false)
    }
  }

  const handleDisconfirm = async () => {
    if (!daily) return
    setConfirmLoading(true)
    try {
      await disconfirmAttendance(daily.id)
      toast.success('Attendance removed.')
      fetchDaily()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to remove attendance')
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
      toast.success('Status updated!')
      setStatusDialog(null)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to update status')
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
      toast.success('Champion photo uploaded!')
      fetchDaily()
    } catch (err: unknown) {
      const ex = err as { response?: { data?: { message?: string } } }
      toast.error(ex?.response?.data?.message ?? 'Failed to upload photo')
    } finally {
      setUploadLoading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const isCurrentUserConfirmed =
    user != null && daily != null && daily.confirmedPlayers.some((p) => p.id === user.id)

  const canToggleAttendance =
    daily != null &&
    (daily.status === 'SCHEDULED' || daily.status === 'CONFIRMED')

  const formattedDate =
    daily != null
      ? new Date(daily.dailyDate + 'T12:00:00').toLocaleDateString(undefined, {
          weekday: 'long',
          year: 'numeric',
          month: 'long',
          day: 'numeric',
        })
      : ''

  return (
    <div className="page-enter min-h-screen flex flex-col">
      <NavBar />
      {loading ? (
        <DetailSkeleton />
      ) : accessDenied ? (
        <div className="flex items-center justify-center py-24">
          <div className="text-center">
            <h2 className="text-2xl font-bold mb-2">Access Denied</h2>
            <p className="text-muted-foreground">You are not a member of this pelada.</p>
          </div>
        </div>
      ) : daily == null ? null : (
        <main className="container max-w-4xl mx-auto px-4 py-6">
          {/* Header */}
          <div className="mb-6">
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
                  Live
                </span>
              )}
            </div>
            <p className="text-sm text-muted-foreground mt-1">{daily.dailyTime}</p>
          </div>

          {/* Attendance action */}
          {canToggleAttendance && (
            <div className="mb-6">
              {isCurrentUserConfirmed ? (
                <button
                  className="text-sm bg-destructive text-destructive-foreground px-4 py-2 rounded disabled:opacity-50"
                  disabled={confirmLoading}
                  onClick={handleDisconfirm}
                >
                  {confirmLoading ? 'Updating...' : 'Disconfirm Attendance'}
                </button>
              ) : (
                <Button
                  variant="gradient"
                  disabled={confirmLoading}
                  onClick={handleConfirm}
                >
                  {confirmLoading ? 'Updating...' : 'Confirm Attendance'}
                </Button>
              )}
            </div>
          )}

          {/* Admin action bar */}
          {daily.isAdmin && (daily.status === 'SCHEDULED' || daily.status === 'CONFIRMED' || daily.status === 'IN_COURSE') && (
            <div className="mb-6 rounded-xl border p-3 flex flex-wrap gap-2 bg-muted/40">
              {daily.status === 'SCHEDULED' && (
                <Button
                  className="bg-gradient-to-br from-yellow-400 to-amber-500 text-white hover:from-yellow-300 hover:to-amber-400 transition-all duration-150"
                  onClick={() =>
                    setStatusDialog({
                      targetStatus: 'CONFIRMED',
                      description: 'This will mark the daily as Confirmed, signalling that attendance is locked in.',
                    })
                  }
                >
                  Confirm Daily
                </Button>
              )}
              {daily.status === 'CONFIRMED' && (
                <Button
                  variant="gradient"
                  onClick={() =>
                    setStatusDialog({
                      targetStatus: 'IN_COURSE',
                      description: 'This will mark the session as In Course. The session is now live.',
                    })
                  }
                >
                  Start Session
                </Button>
              )}
              {(daily.status === 'SCHEDULED' || daily.status === 'CONFIRMED') && (
                <Button
                  variant="outline"
                  className="border-destructive text-destructive hover:bg-destructive/10 hover:text-destructive"
                  onClick={() =>
                    setStatusDialog({
                      targetStatus: 'CANCELED',
                      description: 'This will cancel the daily session. This action cannot be undone.',
                    })
                  }
                >
                  Cancel Daily
                </Button>
              )}
              {daily.status === 'IN_COURSE' && (
                <>
                  <Button variant="gradient" onClick={() => setResultsOpen(true)}>
                    Enter Results / Finalize
                  </Button>
                  {daily.matches.length > 0 && (
                    <Button variant="gradient" onClick={() => setFinalizeOpen(true)}>
                      Finalize Daily
                    </Button>
                  )}
                </>
              )}
            </div>
          )}

          {/* Results modal */}
          {resultsOpen && daily && (
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
          {finalizeOpen && daily && (
            <FinalizeModal
              daily={daily}
              onClose={() => setFinalizeOpen(false)}
              onSuccess={(updated) => {
                setDaily(updated)
                setFinalizeOpen(false)
              }}
            />
          )}

          {/* Confirmation dialog */}
          {statusDialog && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
              <div className="bg-background rounded-lg shadow-lg p-6 max-w-sm w-full mx-4">
                <h3 className="text-lg font-semibold mb-2">Confirm Action</h3>
                <p className="text-sm text-muted-foreground mb-6">{statusDialog.description}</p>
                <div className="flex gap-3 justify-end">
                  <button
                    className="text-sm px-4 py-2 rounded border hover:bg-muted disabled:opacity-50"
                    disabled={statusLoading}
                    onClick={() => setStatusDialog(null)}
                  >
                    Back
                  </button>
                  <button
                    className="text-sm bg-primary text-primary-foreground px-4 py-2 rounded disabled:opacity-50"
                    disabled={statusLoading}
                    onClick={handleStatusConfirm}
                  >
                    {statusLoading ? 'Updating...' : 'Confirm'}
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Confirmed Players */}
          <section className="mb-8">
            <h2 className="text-lg font-semibold mb-3">
              Confirmed Players ({daily.confirmedPlayers.length})
            </h2>
            {daily.confirmedPlayers.length === 0 ? (
              <p className="text-sm text-muted-foreground">No players confirmed yet.</p>
            ) : (
              <div className="space-y-2">
                {daily.confirmedPlayers.map((player) => (
                  <div key={player.id} className="flex items-center gap-3 p-2 rounded hover:bg-muted">
                    <PlayerAvatar player={player} />
                    <div>
                      <p className="text-sm font-medium">{player.username}</p>
                      {player.position && (
                        <p className="text-xs text-muted-foreground">{player.position}</p>
                      )}
                    </div>
                    <div className="ml-auto text-yellow-400 text-sm">
                      {Array.from({ length: 5 }, (_, i) => (
                        <span key={i}>{i < player.stars ? '★' : '☆'}</span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Teams */}
          <TeamsSection
            daily={daily}
            sortLoading={sortLoading}
            swapLoading={swapLoading}
            selectedPlayer={selectedPlayer}
            onSortTeams={handleSortTeams}
            onPlayerClick={handlePlayerClick}
          />

          {/* Finished sections */}
          {daily.status === 'FINISHED' && (
            <>
              {/* Match Results */}
              {daily.matches.length > 0 && (
                <section className="mb-8">
                  <h2 className="text-lg font-semibold mb-3">Match Results</h2>
                  <div className="space-y-2">
                    {daily.matches.map((match) => {
                      const isDraw =
                        match.team1Score !== null &&
                        match.team2Score !== null &&
                        match.team1Score === match.team2Score
                      const team1Won = match.winnerId === match.team1Id
                      const team2Won = match.winnerId === match.team2Id
                      return (
                        <div key={match.id} className="border rounded-lg p-3">
                          <div className="flex items-center justify-center gap-3 text-sm">
                            <span className={`text-right flex-1 ${team1Won ? 'font-bold' : ''}`}>
                              {match.team1Name}
                            </span>
                            <span className="text-muted-foreground font-mono">
                              {match.team1Score ?? '–'} – {match.team2Score ?? '–'}
                            </span>
                            <span className={`text-left flex-1 ${team2Won ? 'font-bold' : ''}`}>
                              {match.team2Name}
                            </span>
                          </div>
                          {isDraw && (
                            <p className="text-center text-xs text-muted-foreground mt-1">Draw</p>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </section>
              )}

              {/* League Table */}
              {daily.leagueTable.length > 0 && (
                <section className="mb-8">
                  <h2 className="text-lg font-semibold mb-3">League Table</h2>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b text-muted-foreground text-xs">
                          <th className="text-left pb-2 pr-2 font-medium">#</th>
                          <th className="text-left pb-2 font-medium">Team</th>
                          <th className="text-center pb-2 px-2 font-medium">W</th>
                          <th className="text-center pb-2 px-2 font-medium">D</th>
                          <th className="text-center pb-2 px-2 font-medium">L</th>
                          <th className="text-center pb-2 px-2 font-medium">GF</th>
                          <th className="text-center pb-2 px-2 font-medium">GA</th>
                          <th className="text-center pb-2 px-2 font-medium">GD</th>
                          <th className="text-center pb-2 px-2 font-medium">Pts</th>
                        </tr>
                      </thead>
                      <tbody>
                        {daily.leagueTable.map((entry) => {
                          const isFirst = entry.position === 1
                          return (
                            <tr
                              key={entry.teamId}
                              className={`border-b last:border-0 ${isFirst ? 'bg-yellow-50 dark:bg-yellow-950/20' : ''}`}
                            >
                              <td className="py-2 pr-2 text-muted-foreground">{entry.position}</td>
                              <td className={`py-2 ${isFirst ? 'font-semibold' : ''}`}>{entry.teamName}</td>
                              <td className="py-2 text-center px-2">{entry.wins}</td>
                              <td className="py-2 text-center px-2">{entry.draws}</td>
                              <td className="py-2 text-center px-2">{entry.losses}</td>
                              <td className="py-2 text-center px-2">{entry.goalsFor}</td>
                              <td className="py-2 text-center px-2">{entry.goalsAgainst}</td>
                              <td className="py-2 text-center px-2">
                                {entry.goalDiff > 0 ? `+${entry.goalDiff}` : entry.goalDiff}
                              </td>
                              <td className="py-2 text-center px-2 font-semibold">{entry.points}</td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                </section>
              )}

              {/* Player Stats */}
              {daily.playerStats.length > 0 && (
                <section className="mb-8">
                  <h2 className="text-lg font-semibold mb-3">Player Stats</h2>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b text-muted-foreground text-xs">
                          <th className="text-left pb-2 font-medium">Player</th>
                          <th className="text-center pb-2 px-2 font-medium">Goals</th>
                          <th className="text-center pb-2 px-2 font-medium">Assists</th>
                          <th className="text-center pb-2 px-2 font-medium">Matches</th>
                          <th className="text-center pb-2 px-2 font-medium">Wins</th>
                        </tr>
                      </thead>
                      <tbody>
                        {[...daily.playerStats]
                          .sort((a, b) => b.goals - a.goals || b.assists - a.assists)
                          .map((stat) => (
                            <tr key={stat.userId} className="border-b last:border-0">
                              <td className="py-2">{stat.username}</td>
                              <td className="py-2 text-center px-2">{stat.goals}</td>
                              <td className="py-2 text-center px-2">{stat.assists}</td>
                              <td className="py-2 text-center px-2">{stat.matchesPlayed}</td>
                              <td className="py-2 text-center px-2">{stat.wins}</td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                </section>
              )}

              {/* Awards */}
              {daily.award &&
                (daily.award.puskasWinnerName || daily.award.wiltballWinnerName) && (
                  <section className="mb-8">
                    <h2 className="text-lg font-semibold mb-3">Awards</h2>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      {daily.award.puskasWinnerName && (
                        <div className="border rounded-lg p-4 flex items-center gap-3">
                          {(() => {
                            const p = daily.confirmedPlayers.find(
                              (cp) => cp.id === daily.award!.puskasWinnerId,
                            )
                            return p ? (
                              <PlayerAvatar player={p} />
                            ) : (
                              <div className="h-10 w-10 rounded-full bg-muted flex-shrink-0" />
                            )
                          })()}
                          <div>
                            <p className="text-xs text-muted-foreground">Puskas Award</p>
                            <p className="font-semibold">{daily.award.puskasWinnerName}</p>
                          </div>
                        </div>
                      )}
                      {daily.award.wiltballWinnerName && (
                        <div className="border rounded-lg p-4 flex items-center gap-3">
                          {(() => {
                            const p = daily.confirmedPlayers.find(
                              (cp) => cp.id === daily.award!.wiltballWinnerId,
                            )
                            return p ? (
                              <PlayerAvatar player={p} />
                            ) : (
                              <div className="h-10 w-10 rounded-full bg-muted flex-shrink-0" />
                            )
                          })()}
                          <div>
                            <p className="text-xs text-muted-foreground">Wiltball Award</p>
                            <p className="font-semibold">{daily.award.wiltballWinnerName}</p>
                          </div>
                        </div>
                      )}
                    </div>
                  </section>
                )}

              {/* Champion Image */}
              <section className="mb-8">
                <h2 className="text-lg font-semibold mb-3">Champion Photo</h2>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  className="hidden"
                  onChange={handleChampionUpload}
                />
                {daily.championImage ? (
                  <div className="space-y-3">
                    <img
                      src={`/api/v1/files/${daily.championImage}`}
                      alt="Champion photo"
                      className="rounded-lg max-w-full"
                    />
                    {daily.isAdmin && (
                      <button
                        className="text-sm text-muted-foreground underline disabled:opacity-50"
                        disabled={uploadLoading}
                        onClick={() => fileInputRef.current?.click()}
                      >
                        {uploadLoading ? 'Uploading...' : 'Replace Photo'}
                      </button>
                    )}
                  </div>
                ) : daily.isAdmin ? (
                  <div>
                    <p className="text-sm text-muted-foreground mb-2">
                      No champion photo uploaded yet.
                    </p>
                    <button
                      className="text-sm bg-primary text-primary-foreground px-4 py-2 rounded disabled:opacity-50"
                      disabled={uploadLoading}
                      onClick={() => fileInputRef.current?.click()}
                    >
                      {uploadLoading ? 'Uploading...' : 'Upload Champion Photo'}
                    </button>
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">No champion photo uploaded yet.</p>
                )}
              </section>
            </>
          )}
        </main>
      )}
    </div>
  )
}
