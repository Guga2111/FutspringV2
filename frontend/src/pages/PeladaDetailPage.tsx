import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import NavBar from '../components/NavBar'
import { getPelada } from '../api/peladas'
import type { PeladaDetail } from '../types/pelada'

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

export default function PeladaDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [pelada, setPelada] = useState<PeladaDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    getPelada(Number(id))
      .then(setPelada)
      .catch((err) => {
        if (err?.response?.status === 403) {
          setAccessDenied(true)
        }
      })
      .finally(() => setLoading(false))
  }, [id])

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
            <h2 className="text-lg font-semibold mb-3 mt-4">
              Members ({pelada.members.length})
            </h2>
            <div className="space-y-3">
              {pelada.members.map((member) => (
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
                </div>
              ))}
            </div>
          </div>
        </main>
      ) : (
        <div className="flex items-center justify-center py-24">
          <p className="text-muted-foreground">Pelada not found.</p>
        </div>
      )}
    </div>
  )
}
