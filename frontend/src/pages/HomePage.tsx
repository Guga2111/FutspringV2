import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import NavBar from '../components/NavBar'
import { Card, CardContent } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Badge } from '../components/ui/badge'
import { Skeleton } from '../components/ui/skeleton'
import { getMyPeladas } from '../api/peladas'
import { getDailiesForPelada } from '../api/dailies'
import type { PeladaResponse } from '../types/pelada'
import type { DailyListItem } from '../types/daily'
import CreatePeladaModal from '../components/CreatePeladaModal'

function PeladaCardSkeleton() {
  return (
    <Card className="overflow-hidden rounded-xl border shadow-sm">
      <Skeleton className="h-40 w-full rounded-none" />
      <CardContent className="p-4">
        <Skeleton className="h-5 w-3/4 mb-2" />
        <Skeleton className="h-4 w-1/2 mb-2" />
        <Skeleton className="h-4 w-1/3" />
      </CardContent>
    </Card>
  )
}

function getNextSession(dailies: DailyListItem[]): string | null {
  const upcoming = dailies
    .filter((d) => d.status === 'SCHEDULED' || d.status === 'CONFIRMED')
    .sort((a, b) => a.dailyDate.localeCompare(b.dailyDate))
  return upcoming.length > 0 ? upcoming[0].dailyDate : null
}

function PeladaCard({ pelada, nextSession }: { pelada: PeladaResponse; nextSession: string | null | undefined }) {
  return (
    <Link to={`/pelada/${pelada.id}`} className="block hover:opacity-90 transition-opacity">
      <Card className="overflow-hidden rounded-xl border shadow-sm">
        {pelada.image ? (
          <img
            src={`/api/v1/files/${pelada.image}`}
            alt={pelada.name}
            className="h-40 w-full object-cover"
          />
        ) : (
          <div className="h-40 bg-gradient-to-br from-green-500 to-emerald-700 flex items-center justify-center">
            <span className="text-4xl">⚽</span>
          </div>
        )}
        <CardContent className="p-4">
          <h2 className="font-bold text-lg leading-tight">{pelada.name}</h2>
          <p className="text-sm text-muted-foreground mt-1">
            {pelada.dayOfWeek} · {pelada.timeOfDay}
          </p>
          <div className="flex items-center gap-2 mt-2">
            <Badge variant="secondary">{pelada.memberCount} members</Badge>
            <span className="text-xs text-muted-foreground">
              {nextSession === undefined
                ? '…'
                : nextSession
                ? nextSession
                : 'No upcoming session'}
            </span>
          </div>
        </CardContent>
      </Card>
    </Link>
  )
}

export default function HomePage() {
  const [peladas, setPeladas] = useState<PeladaResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [nextSessions, setNextSessions] = useState<Record<number, string | null>>({})

  function fetchPeladas() {
    setLoading(true)
    getMyPeladas()
      .then((data) => {
        setPeladas(data)
        // Fetch dailies for each pelada in parallel
        Promise.all(
          data.map((p) =>
            getDailiesForPelada(p.id)
              .then((dailies) => ({ id: p.id, next: getNextSession(dailies) }))
              .catch(() => ({ id: p.id, next: null }))
          )
        ).then((results) => {
          const map: Record<number, string | null> = {}
          results.forEach(({ id, next }) => { map[id] = next })
          setNextSessions(map)
        })
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchPeladas()
  }, [])

  return (
    <div className="page-enter min-h-screen flex flex-col">
      <NavBar />
      {showCreateModal && (
        <CreatePeladaModal
          onClose={() => setShowCreateModal(false)}
          onCreated={() => { setShowCreateModal(false); fetchPeladas() }}
        />
      )}
      <main className="flex-1 container max-w-5xl mx-auto px-4 py-8">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold tracking-tight">My Peladas</h1>
          <Button variant="gradient" onClick={() => setShowCreateModal(true)}>+ New Pelada</Button>
        </div>
        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <PeladaCardSkeleton />
            <PeladaCardSkeleton />
            <PeladaCardSkeleton />
          </div>
        ) : peladas.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <span className="text-5xl mb-4">⚽</span>
            <p className="text-muted-foreground text-lg mb-6">
              You're not in any pelada yet. Create one to get started.
            </p>
            <Button variant="gradient" onClick={() => setShowCreateModal(true)}>+ New Pelada</Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {peladas.map((pelada) => (
              <PeladaCard key={pelada.id} pelada={pelada} nextSession={nextSessions[pelada.id]} />
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
