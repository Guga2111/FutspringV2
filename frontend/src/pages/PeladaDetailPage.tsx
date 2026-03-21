import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import NavBar from '../components/NavBar'
import { getPelada, addPlayer, removePlayer, setAdmin, searchUsers, deletePelada, getRanking } from '../api/peladas'
import { getDailiesForPelada, createDaily } from '../api/dailies'
import type { PeladaDetail, PeladaMember } from '../types/pelada'
import type { UserResponseDTO } from '../types/auth'
import type { DailyListItem, RankingDTO } from '../types/daily'
import { useAuth } from '../hooks/useAuth'
import EditPeladaModal from '../components/EditPeladaModal'

function SkeletonBlock({ className }: { className: string }) {
  return <div className={`bg-muted rounded animate-pulse ${className}`} />
}

function DetailSkeleton() {
  return (
    <div>
      <SkeletonBlock className="h-56 w-full rounded-none" />
      <div className="container max-w-4xl mx-auto px-4 py-6">
        <SkeletonBlock className="h-8 w-1/2 mb-3" />
        <SkeletonBlock className="h-4 w-1/3 mb-2" />
        <SkeletonBlock className="h-4 w-1/4 mb-2" />
        <SkeletonBlock className="h-4 w-2/5 mb-6" />
        <SkeletonBlock className="h-6 w-32 mb-4" />
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex items-center gap-3 mb-3">
            <SkeletonBlock className="h-10 w-10 rounded-full flex-shrink-0" />
            <div className="flex-1">
              <SkeletonBlock className="h-4 w-32 mb-1" />
              <SkeletonBlock className="h-3 w-20" />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function StarRating({ stars }: { stars: number }) {
  return (
    <span className="text-yellow-400 text-sm">
      {Array.from({ length: 5 }, (_, i) => (
        <span key={i}>{i < stars ? '★' : '☆'}</span>
      ))}
    </span>
  )
}

function MemberAvatar({ username, image }: { username: string; image: string | null }) {
  if (image) {
    return (
      <img
        src={`/api/v1/files/${image}`}
        alt={username}
        className="h-10 w-10 rounded-full object-cover flex-shrink-0"
      />
    )
  }
  const initials = username.slice(0, 2).toUpperCase()
  return (
    <div className="h-10 w-10 rounded-full bg-muted flex items-center justify-center flex-shrink-0 text-sm font-semibold">
      {initials}
    </div>
  )
}

// Add Player Dialog
function AddPlayerDialog({
  peladaId,
  existingMemberIds,
  onClose,
  onAdded,
}: {
  peladaId: number
  existingMemberIds: Set<number>
  onClose: () => void
  onAdded: () => void
}) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<UserResponseDTO[]>([])
  const [searching, setSearching] = useState(false)
  const [adding, setAdding] = useState<number | null>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const handleQueryChange = (q: string) => {
    setQuery(q)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (!q.trim()) {
      setResults([])
      return
    }
    debounceRef.current = setTimeout(async () => {
      setSearching(true)
      try {
        const data = await searchUsers(q.trim())
        setResults(data)
      } catch {
        // ignore
      } finally {
        setSearching(false)
      }
    }, 300)
  }

  const handleAdd = async (user: UserResponseDTO) => {
    setAdding(user.id)
    try {
      await addPlayer(peladaId, user.id)
      toast.success(`${user.username} added to pelada`)
      onAdded()
      onClose()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to add player')
    } finally {
      setAdding(null)
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4" onClick={onClose}>
      <div
        className="bg-background rounded-lg shadow-lg w-full max-w-md p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold mb-4">Add Player</h3>
        <input
          className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm mb-3"
          placeholder="Search by username or email..."
          value={query}
          onChange={(e) => handleQueryChange(e.target.value)}
          autoFocus
        />
        {searching && <p className="text-sm text-muted-foreground mb-2">Searching...</p>}
        <div className="space-y-2 max-h-64 overflow-y-auto">
          {results.map((user) => {
            const alreadyMember = existingMemberIds.has(user.id)
            return (
              <div key={user.id} className="flex items-center gap-3 p-2 rounded hover:bg-muted">
                <div className="h-8 w-8 rounded-full bg-muted flex items-center justify-center text-xs font-semibold flex-shrink-0">
                  {user.username.slice(0, 2).toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{user.username}</p>
                  <p className="text-xs text-muted-foreground truncate">{user.email}</p>
                </div>
                {alreadyMember ? (
                  <span className="text-xs text-muted-foreground">Member</span>
                ) : (
                  <button
                    className="text-xs bg-primary text-primary-foreground px-3 py-1 rounded disabled:opacity-50"
                    disabled={adding === user.id}
                    onClick={() => handleAdd(user)}
                  >
                    {adding === user.id ? '...' : 'Add'}
                  </button>
                )}
              </div>
            )
          })}
          {!searching && query.trim() && results.length === 0 && (
            <p className="text-sm text-muted-foreground text-center py-4">No users found</p>
          )}
        </div>
        <div className="flex justify-end mt-4">
          <button
            className="text-sm text-muted-foreground hover:underline"
            onClick={onClose}
          >
            Close
          </button>
        </div>
      </div>
    </div>
  )
}

// Confirm Remove Dialog
function ConfirmRemoveDialog({
  member,
  onConfirm,
  onClose,
  loading,
}: {
  member: PeladaMember
  onConfirm: () => void
  onClose: () => void
  loading: boolean
}) {
  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4" onClick={onClose}>
      <div
        className="bg-background rounded-lg shadow-lg w-full max-w-sm p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold mb-2">Remove Player</h3>
        <p className="text-sm text-muted-foreground mb-6">
          Remove <strong>{member.username}</strong> from this pelada?
        </p>
        <div className="flex gap-3 justify-end">
          <button className="text-sm text-muted-foreground hover:underline" onClick={onClose} disabled={loading}>
            Cancel
          </button>
          <button
            className="text-sm bg-destructive text-destructive-foreground px-4 py-2 rounded disabled:opacity-50"
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? 'Removing...' : 'Remove'}
          </button>
        </div>
      </div>
    </div>
  )
}

const STATUS_COLORS: Record<string, string> = {
  SCHEDULED: 'bg-blue-100 text-blue-800',
  CONFIRMED: 'bg-yellow-100 text-yellow-800',
  IN_COURSE: 'bg-green-100 text-green-800',
  FINISHED: 'bg-gray-100 text-gray-800',
  CANCELED: 'bg-red-100 text-red-800',
}

function StatusBadge({ status }: { status: string }) {
  const cls = STATUS_COLORS[status] ?? 'bg-muted text-muted-foreground'
  return (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${cls}`}>
      {status.replace('_', ' ')}
    </span>
  )
}

function CreateSessionDialog({
  peladaId,
  onClose,
  onCreated,
}: {
  peladaId: number
  onClose: () => void
  onCreated: () => void
}) {
  const [dailyDate, setDailyDate] = useState('')
  const [dailyTime, setDailyTime] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!dailyDate || !dailyTime) return
    setSubmitting(true)
    try {
      await createDaily(peladaId, { dailyDate, dailyTime })
      toast.success('Session created!')
      onCreated()
      onClose()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to create session')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4" onClick={onClose}>
      <div
        className="bg-background rounded-lg shadow-lg w-full max-w-sm p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold mb-4">Create Session</h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1" htmlFor="session-date">Date</label>
            <input
              id="session-date"
              type="date"
              required
              className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm"
              value={dailyDate}
              onChange={(e) => setDailyDate(e.target.value)}
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1" htmlFor="session-time">Time</label>
            <input
              id="session-time"
              type="time"
              required
              className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm"
              value={dailyTime}
              onChange={(e) => setDailyTime(e.target.value)}
            />
          </div>
          <div className="flex gap-3 justify-end pt-2">
            <button
              type="button"
              className="text-sm text-muted-foreground hover:underline"
              onClick={onClose}
              disabled={submitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="text-sm bg-primary text-primary-foreground px-4 py-2 rounded disabled:opacity-50"
              disabled={submitting || !dailyDate || !dailyTime}
            >
              {submitting ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function PeladaDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user: currentUser } = useAuth()
  const [pelada, setPelada] = useState<PeladaDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)
  const [showAddPlayer, setShowAddPlayer] = useState(false)
  const [showEdit, setShowEdit] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [confirmRemoveMember, setConfirmRemoveMember] = useState<PeladaMember | null>(null)
  const [removing, setRemoving] = useState(false)
  const [togglingAdmin, setTogglingAdmin] = useState<number | null>(null)
  const [dailies, setDailies] = useState<DailyListItem[]>([])
  const [dailiesLoading, setDailiesLoading] = useState(true)
  const [showCreateSession, setShowCreateSession] = useState(false)
  const [ranking, setRanking] = useState<RankingDTO[]>([])
  const [rankingLoading, setRankingLoading] = useState(true)
  const [rankingSort, setRankingSort] = useState<{ col: 'goals' | 'assists' | 'matchesPlayed' | 'wins'; dir: 'asc' | 'desc' }>({ col: 'goals', dir: 'desc' })

  const fetchPelada = useCallback(() => {
    if (!id) return
    getPelada(Number(id))
      .then(setPelada)
      .catch((err) => {
        if (err?.response?.status === 403) {
          setAccessDenied(true)
        }
      })
      .finally(() => setLoading(false))
  }, [id])

  const fetchDailies = useCallback(() => {
    if (!id) return
    setDailiesLoading(true)
    getDailiesForPelada(Number(id))
      .then(setDailies)
      .catch(() => {
        // ignore errors silently; pelada fetch handles 403
      })
      .finally(() => setDailiesLoading(false))
  }, [id])

  const fetchRanking = useCallback(() => {
    if (!id) return
    setRankingLoading(true)
    getRanking(Number(id))
      .then(setRanking)
      .catch(() => {
        // ignore errors silently
      })
      .finally(() => setRankingLoading(false))
  }, [id])

  useEffect(() => {
    setLoading(true)
    fetchPelada()
  }, [fetchPelada])

  useEffect(() => {
    fetchDailies()
  }, [fetchDailies])

  useEffect(() => {
    fetchRanking()
  }, [fetchRanking])

  type RankingCol = 'goals' | 'assists' | 'matchesPlayed' | 'wins'

  const handleRankingSort = (col: RankingCol) => {
    setRankingSort((prev) =>
      prev.col === col
        ? { col, dir: prev.dir === 'desc' ? 'asc' : 'desc' }
        : { col, dir: 'desc' }
    )
  }

  const sortedRanking = [...ranking].sort((a, b) => {
    const diff = b[rankingSort.col] - a[rankingSort.col]
    return rankingSort.dir === 'desc' ? diff : -diff
  })

  const isCurrentUserAdmin = pelada?.members.find((m) => m.id === currentUser?.id)?.isAdmin ?? false
  const creatorId = pelada?.creatorId ?? null
  const isCurrentUserCreator = currentUser != null && creatorId === currentUser.id

  const handleDelete = async () => {
    if (!pelada) return
    setDeleting(true)
    try {
      await deletePelada(pelada.id)
      toast.success('Pelada deleted')
      navigate('/home')
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to delete pelada')
      setDeleting(false)
      setShowDeleteConfirm(false)
    }
  }

  const handleRemoveConfirm = async () => {
    if (!confirmRemoveMember || !pelada) return
    setRemoving(true)
    try {
      await removePlayer(pelada.id, confirmRemoveMember.id)
      toast.success(`${confirmRemoveMember.username} removed from pelada`)
      setConfirmRemoveMember(null)
      fetchPelada()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to remove player')
    } finally {
      setRemoving(false)
    }
  }

  const handleToggleAdmin = async (member: PeladaMember) => {
    if (!pelada) return
    setTogglingAdmin(member.id)
    try {
      await setAdmin(pelada.id, member.id, !member.isAdmin)
      toast.success(member.isAdmin ? `${member.username} is no longer an admin` : `${member.username} is now an admin`)
      fetchPelada()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } }
      toast.error(e?.response?.data?.message ?? 'Failed to update admin status')
    } finally {
      setTogglingAdmin(null)
    }
  }

  return (
    <div className="min-h-screen flex flex-col">
      <NavBar />
      {loading ? (
        <DetailSkeleton />
      ) : accessDenied ? (
        <div className="flex flex-col items-center justify-center py-24 text-center">
          <span className="text-5xl mb-4">🚫</span>
          <h2 className="text-xl font-semibold mb-2">Access Denied</h2>
          <p className="text-muted-foreground">You are not a member of this pelada.</p>
        </div>
      ) : pelada ? (
        <main>
          {/* Header image / banner */}
          {pelada.image ? (
            <img
              src={`/api/v1/files/${pelada.image}`}
              alt={pelada.name}
              className="h-56 w-full object-cover"
            />
          ) : (
            <div className="h-56 bg-muted flex items-center justify-center">
              <span className="text-6xl">⚽</span>
            </div>
          )}

          <div className="container max-w-4xl mx-auto px-4 py-6">
            {/* Pelada info */}
            <div className="flex items-start justify-between gap-4 mb-1">
              <h1 className="text-2xl font-bold">{pelada.name}</h1>
              <div className="flex items-center gap-2 flex-shrink-0">
                {isCurrentUserAdmin && (
                  <button
                    className="text-sm border border-border px-3 py-1.5 rounded hover:bg-muted"
                    onClick={() => setShowEdit(true)}
                  >
                    Edit
                  </button>
                )}
                {isCurrentUserCreator && (
                  <button
                    className="text-sm border border-destructive text-destructive px-3 py-1.5 rounded hover:bg-destructive hover:text-destructive-foreground"
                    onClick={() => setShowDeleteConfirm(true)}
                  >
                    Delete Pelada
                  </button>
                )}
              </div>
            </div>
            <p className="text-muted-foreground mb-1">
              {pelada.dayOfWeek} · {pelada.timeOfDay}
            </p>
            <p className="text-sm text-muted-foreground mb-1">
              Duration: {pelada.duration}h
            </p>
            {pelada.address && (
              <p className="text-sm text-muted-foreground mb-1">
                📍 {pelada.address}
              </p>
            )}
            {pelada.reference && (
              <p className="text-sm text-muted-foreground mb-4">
                Reference: {pelada.reference}
              </p>
            )}

            {/* Members section */}
            <div className="flex items-center justify-between mt-4 mb-3">
              <h2 className="text-lg font-semibold">
                Members ({pelada.members.length})
              </h2>
              {isCurrentUserAdmin && (
                <button
                  className="text-sm bg-primary text-primary-foreground px-4 py-2 rounded"
                  onClick={() => setShowAddPlayer(true)}
                >
                  + Add Player
                </button>
              )}
            </div>
            <div className="space-y-3">
              {pelada.members.map((member) => {
                const isCreator = member.id === creatorId
                return (
                  <div key={member.id} className="flex items-center gap-3">
                    <MemberAvatar username={member.username} image={member.image} />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-medium truncate">{member.username}</span>
                        {member.isAdmin && (
                          <span className="text-xs bg-primary text-primary-foreground px-2 py-0.5 rounded-full">
                            Admin
                          </span>
                        )}
                        {member.position && (
                          <span className="text-xs bg-muted px-2 py-0.5 rounded-full">
                            {member.position}
                          </span>
                        )}
                      </div>
                      <StarRating stars={member.stars} />
                    </div>
                    {isCurrentUserAdmin && !isCreator && (
                      <div className="flex items-center gap-2 flex-shrink-0">
                        <button
                          className="text-xs border border-border px-2 py-1 rounded hover:bg-muted disabled:opacity-50"
                          disabled={togglingAdmin === member.id}
                          onClick={() => handleToggleAdmin(member)}
                        >
                          {togglingAdmin === member.id
                            ? '...'
                            : member.isAdmin
                            ? 'Remove Admin'
                            : 'Make Admin'}
                        </button>
                        <button
                          className="text-xs text-destructive border border-destructive px-2 py-1 rounded hover:bg-destructive hover:text-destructive-foreground disabled:opacity-50"
                          onClick={() => setConfirmRemoveMember(member)}
                        >
                          Remove
                        </button>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>

            {/* Ranking section */}
            <div className="mt-8">
              <h2 className="text-lg font-semibold mb-3">Ranking</h2>
              {rankingLoading ? (
                <div className="space-y-2">
                  {[0, 1, 2].map((i) => (
                    <div key={i} className="flex items-center gap-3">
                      <SkeletonBlock className="h-8 w-8 rounded-full flex-shrink-0" />
                      <SkeletonBlock className="h-4 flex-1" />
                      <SkeletonBlock className="h-4 w-8" />
                      <SkeletonBlock className="h-4 w-8" />
                      <SkeletonBlock className="h-4 w-8" />
                      <SkeletonBlock className="h-4 w-8" />
                    </div>
                  ))}
                </div>
              ) : sortedRanking.length === 0 ? (
                <p className="text-sm text-muted-foreground">No ranking data yet.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b text-muted-foreground text-xs">
                        <th className="text-left pb-2 font-medium">Player</th>
                        {(['goals', 'assists', 'matchesPlayed', 'wins'] as const).map((col) => {
                          const labels: Record<string, string> = { goals: 'Goals', assists: 'Assists', matchesPlayed: 'Matches', wins: 'Wins' }
                          const active = rankingSort.col === col
                          return (
                            <th
                              key={col}
                              className="text-center pb-2 px-2 font-medium cursor-pointer select-none hover:text-foreground"
                              onClick={() => handleRankingSort(col)}
                            >
                              {labels[col]}{active ? (rankingSort.dir === 'desc' ? ' ▾' : ' ▴') : ''}
                            </th>
                          )
                        })}
                      </tr>
                    </thead>
                    <tbody>
                      {sortedRanking.map((row) => (
                        <tr key={row.userId} className="border-b last:border-0">
                          <td className="py-2 pr-2">
                            <div className="flex items-center gap-2">
                              {row.userImage ? (
                                <img
                                  src={`/api/v1/files/${row.userImage}`}
                                  alt={row.username}
                                  className="h-7 w-7 rounded-full object-cover flex-shrink-0"
                                />
                              ) : (
                                <div className="h-7 w-7 rounded-full bg-muted flex items-center justify-center flex-shrink-0 text-xs font-semibold">
                                  {row.username.slice(0, 2).toUpperCase()}
                                </div>
                              )}
                              <span className="font-medium">{row.username}</span>
                            </div>
                          </td>
                          <td className="text-center py-2 px-2">{row.goals}</td>
                          <td className="text-center py-2 px-2">{row.assists}</td>
                          <td className="text-center py-2 px-2">{row.matchesPlayed}</td>
                          <td className="text-center py-2 px-2">{row.wins}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Sessions section */}
            <div className="mt-8">
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-lg font-semibold">Sessions</h2>
                {isCurrentUserAdmin && (
                  <button
                    className="text-sm bg-primary text-primary-foreground px-4 py-2 rounded"
                    onClick={() => setShowCreateSession(true)}
                  >
                    + Create Session
                  </button>
                )}
              </div>
              {dailiesLoading ? (
                <div className="space-y-3">
                  {[0, 1, 2].map((i) => (
                    <div key={i} className="flex items-center gap-3">
                      <SkeletonBlock className="h-4 w-24" />
                      <SkeletonBlock className="h-4 w-16" />
                      <SkeletonBlock className="h-5 w-20 rounded-full" />
                      <SkeletonBlock className="h-4 w-12" />
                    </div>
                  ))}
                </div>
              ) : dailies.length === 0 ? (
                <p className="text-sm text-muted-foreground">No sessions yet.</p>
              ) : (
                <div className="space-y-2">
                  {dailies.map((daily) => (
                    <Link
                      key={daily.id}
                      to={`/daily/${daily.id}`}
                      className="flex items-center gap-3 p-3 rounded-lg border border-border hover:bg-muted transition-colors"
                    >
                      <span className="text-sm font-medium">
                        {new Date(daily.dailyDate + 'T12:00:00').toLocaleDateString('en-US', {
                          month: 'short',
                          day: 'numeric',
                          year: 'numeric',
                        })}
                      </span>
                      <span className="text-sm text-muted-foreground">{daily.dailyTime}</span>
                      <StatusBadge status={daily.status} />
                      <span className="text-sm text-muted-foreground ml-auto">
                        {daily.confirmedPlayerCount} players
                      </span>
                    </Link>
                  ))}
                </div>
              )}
            </div>
          </div>
        </main>
      ) : (
        <div className="flex items-center justify-center py-24">
          <p className="text-muted-foreground">Pelada not found.</p>
        </div>
      )}

      {showAddPlayer && pelada && (
        <AddPlayerDialog
          peladaId={pelada.id}
          existingMemberIds={new Set(pelada.members.map((m) => m.id))}
          onClose={() => setShowAddPlayer(false)}
          onAdded={fetchPelada}
        />
      )}

      {confirmRemoveMember && (
        <ConfirmRemoveDialog
          member={confirmRemoveMember}
          onConfirm={handleRemoveConfirm}
          onClose={() => setConfirmRemoveMember(null)}
          loading={removing}
        />
      )}

      {showEdit && pelada && (
        <EditPeladaModal
          pelada={pelada}
          onClose={() => setShowEdit(false)}
          onUpdated={fetchPelada}
        />
      )}

      {showCreateSession && pelada && (
        <CreateSessionDialog
          peladaId={pelada.id}
          onClose={() => setShowCreateSession(false)}
          onCreated={fetchDailies}
        />
      )}

      {showDeleteConfirm && pelada && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4" onClick={() => !deleting && setShowDeleteConfirm(false)}>
          <div
            className="bg-background rounded-lg shadow-lg w-full max-w-sm p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-lg font-semibold mb-2">Delete Pelada</h3>
            <p className="text-sm text-muted-foreground mb-2">
              Are you sure you want to delete <strong>{pelada.name}</strong>?
            </p>
            <p className="text-sm text-destructive mb-6">
              This action cannot be undone. All members and data will be removed.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                className="text-sm text-muted-foreground hover:underline"
                onClick={() => setShowDeleteConfirm(false)}
                disabled={deleting}
              >
                Cancel
              </button>
              <button
                className="text-sm bg-destructive text-destructive-foreground px-4 py-2 rounded disabled:opacity-50"
                onClick={handleDelete}
                disabled={deleting}
              >
                {deleting ? 'Deleting...' : 'Delete Pelada'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
