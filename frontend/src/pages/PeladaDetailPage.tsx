import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { toast } from 'sonner'
import NavBar from '../components/NavBar'
import { getPelada, addPlayer, removePlayer, setAdmin, searchUsers } from '../api/peladas'
import type { PeladaDetail, PeladaMember } from '../types/pelada'
import type { UserResponseDTO } from '../types/auth'
import { useAuth } from '../hooks/useAuth'

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

export default function PeladaDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { user: currentUser } = useAuth()
  const [pelada, setPelada] = useState<PeladaDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)
  const [showAddPlayer, setShowAddPlayer] = useState(false)
  const [confirmRemoveMember, setConfirmRemoveMember] = useState<PeladaMember | null>(null)
  const [removing, setRemoving] = useState(false)
  const [togglingAdmin, setTogglingAdmin] = useState<number | null>(null)

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

  useEffect(() => {
    setLoading(true)
    fetchPelada()
  }, [fetchPelada])

  const isCurrentUserAdmin = pelada?.members.find((m) => m.id === currentUser?.id)?.isAdmin ?? false
  const creatorId = pelada?.creatorId ?? null

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
            <h1 className="text-2xl font-bold mb-1">{pelada.name}</h1>
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
    </div>
  )
}
