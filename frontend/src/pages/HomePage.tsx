import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import NavBar from '../components/NavBar'
import { Card, CardContent } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { getMyPeladas } from '../api/peladas'
import type { PeladaResponse } from '../types/pelada'
import CreatePeladaModal from '../components/CreatePeladaModal'

function PeladaCardSkeleton() {
  return (
    <Card className="overflow-hidden animate-pulse">
      <div className="h-40 bg-muted" />
      <CardContent className="p-4">
        <div className="h-5 bg-muted rounded w-3/4 mb-2" />
        <div className="h-4 bg-muted rounded w-1/2" />
      </CardContent>
    </Card>
  )
}

function PeladaCard({ pelada }: { pelada: PeladaResponse }) {
  return (
    <Link to={`/pelada/${pelada.id}`} className="block hover:opacity-90 transition-opacity">
      <Card className="overflow-hidden">
        {pelada.image ? (
          <img
            src={`/api/v1/files/${pelada.image}`}
            alt={pelada.name}
            className="h-40 w-full object-cover"
          />
        ) : (
          <div className="h-40 bg-muted flex items-center justify-center">
            <span className="text-4xl">⚽</span>
          </div>
        )}
        <CardContent className="p-4">
          <h2 className="font-semibold text-lg leading-tight">{pelada.name}</h2>
          <p className="text-sm text-muted-foreground mt-1">
            {pelada.dayOfWeek} · {pelada.timeOfDay}
          </p>
        </CardContent>
      </Card>
    </Link>
  )
}

export default function HomePage() {
  const [peladas, setPeladas] = useState<PeladaResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreateModal, setShowCreateModal] = useState(false)

  function fetchPeladas() {
    setLoading(true)
    getMyPeladas()
      .then(setPeladas)
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchPeladas()
  }, [])

  return (
    <div className="min-h-screen flex flex-col">
      <NavBar />
      {showCreateModal && (
        <CreatePeladaModal
          onClose={() => setShowCreateModal(false)}
          onCreated={() => { setShowCreateModal(false); fetchPeladas() }}
        />
      )}
      <main className="flex-1 container max-w-5xl mx-auto px-4 py-8">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold">My Peladas</h1>
          <Button variant="gradient" onClick={() => setShowCreateModal(true)}>Create Pelada</Button>
        </div>
        {loading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
            <PeladaCardSkeleton />
            <PeladaCardSkeleton />
            <PeladaCardSkeleton />
            <PeladaCardSkeleton />
          </div>
        ) : peladas.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <span className="text-5xl mb-4">⚽</span>
            <p className="text-muted-foreground text-lg">
              No peladas yet. Create one to get started!
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
            {peladas.map((pelada) => (
              <PeladaCard key={pelada.id} pelada={pelada} />
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
