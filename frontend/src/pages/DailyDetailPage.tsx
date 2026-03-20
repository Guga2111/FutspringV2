import { useCallback, useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { toast } from 'sonner'
import NavBar from '../components/NavBar'
import { getDailyDetail, confirmAttendance, disconfirmAttendance } from '../api/dailies'
import type { DailyDetail, PlayerDTO } from '../types/daily'
import { useAuth } from '../hooks/useAuth'

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

export default function DailyDetailPage() {
  const { id } = useParams<{ id: string }>()
  const dailyId = Number(id)
  const { user } = useAuth()

  const [daily, setDaily] = useState<DailyDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)
  const [confirmLoading, setConfirmLoading] = useState(false)

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
    <div className="min-h-screen flex flex-col">
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
              <h1 className="text-2xl font-bold">
                {formattedDate}
              </h1>
              <StatusBadge status={daily.status} />
            </div>
            <p className="text-muted-foreground mt-1">{daily.dailyTime}</p>
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
                <button
                  className="text-sm bg-primary text-primary-foreground px-4 py-2 rounded disabled:opacity-50"
                  disabled={confirmLoading}
                  onClick={handleConfirm}
                >
                  {confirmLoading ? 'Updating...' : 'Confirm Attendance'}
                </button>
              )}
            </div>
          )}

          {/* Confirmed Players */}
          <section>
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
        </main>
      )}
    </div>
  )
}
