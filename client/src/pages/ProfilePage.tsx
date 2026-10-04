import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { getInitials, getPeladaGradient, getFileUrl } from '@/lib/utils'
import { useAuth } from '@/hooks/useAuth'
import { getPositionLabel } from '@/types/user'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useProfile } from '@/components/profile/hooks/useProfile'
import { Separator } from '@/components/ui/separator'
import { Target, Handshake, CalendarDays, Trophy, TrendingUp, Medal, Star, Swords, Award, Zap } from 'lucide-react'
import KpiCard from '@/components/profile/KpiCard'
import ProfilePieChart from '@/components/profile/ProfilePieChart'
import StatsOverTimeChart from '@/components/profile/StatsOverTimeChart'
import MatchHistoryTable from '@/components/profile/MatchHistoryTable'
import EditProfileModal from '@/components/profile/EditProfileModal'
import { Stars, ProfileSkeleton } from '@/components/profile'

export default function ProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { user: currentUser } = useAuth()
  const userId = Number(id)
  const [editOpen, setEditOpen] = useState(false)
  const {
    status, profile, stats, setProfile,
    peladas: profilePeladas, peladasLoading,
    timelinePoints, timelineLoading,
    matchHistory, matchHistoryLoading,
  } = useProfile(userId)

  if (status === 'loading') {
    return <ProfileSkeleton />
  }

  if (!profile || !stats) {
    const message = status === 'forbidden'
      ? 'Você só pode ver o perfil de jogadores das suas peladas.'
      : status === 'notFound'
        ? 'Perfil não encontrado.'
        : 'Não foi possível carregar o perfil.'
    return (
      <div className="flex items-center justify-center min-h-[60vh] px-4 text-center">
        <p className="text-muted-foreground">{message}</p>
      </div>
    )
  }

  const isOwnProfile = currentUser?.id === profile.id
  const bgUrl = getFileUrl(profile.backgroundImage)
  const positionLabel = getPositionLabel(profile.position)

  return (
    <div className="page-enter">
      {/* Background banner */}
      {bgUrl && (
        <div
          className="w-full h-[200px] relative"
          style={{ backgroundImage: `url(${bgUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' }}
        />
      )}

      <div className="max-w-5xl mx-auto px-4 sm:px-6">
        {/* Avatar + name row */}
        <div className={`flex items-end gap-4 mb-6 ${bgUrl ? '-mt-12' : 'mt-6'}`}>
          <Avatar className="size-24 border-4 border-background">
            <AvatarImage src={getFileUrl(profile.image)} alt={profile.username} className="object-cover" />
            <AvatarFallback className="text-xl font-bold">{getInitials(profile.username)}</AvatarFallback>
          </Avatar>
          <div className="flex-1 pb-2">
            <div className="flex items-center gap-3 flex-wrap">
              <h1 className="text-2xl font-bold">{profile.username}</h1>
              {isOwnProfile && (
                <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
                  Editar perfil
                </Button>
              )}
            </div>
            <div className="flex items-center gap-3 mt-1 flex-wrap">
              {positionLabel && <Badge variant="secondary">{positionLabel}</Badge>}
              <Stars count={profile.stars} />
            </div>
          </div>
        </div>

        {/* Performance KPI cards */}
        <section className="mb-10">
          <h2 className="text-xl font-semibold mb-4">Desempenho</h2>
          <Separator className="mb-6" />
          {(() => {
            const sessions = stats.sessionsPlayed ?? 0
            const wins = stats.matchWins ?? 0
            const matches = stats.matchesPlayed ?? 0
            const contributions = stats.goals + stats.assists
            const winPct = matches > 0 ? `${Math.round((wins / matches) * 100)}%` : '0%'
            const champPct = sessions > 0 ? `${Math.round((stats.wins / sessions) * 100)}%` : '0%'
            const goalsPer = sessions > 0 ? (stats.goals / sessions).toFixed(1) : '0.0'
            const assistsPer = sessions > 0 ? (stats.assists / sessions).toFixed(1) : '0.0'
            const contribPer = sessions > 0 ? (contributions / sessions).toFixed(1) : '0.0'
            return (
              <>
                <div className="grid grid-cols-3 lg:grid-cols-6 gap-4 mb-4">
                  <KpiCard label="Vitórias" value={wins} icon={<Swords className="w-5 h-5 text-red-400" />} />
                  <KpiCard label="Partidas" value={matches} icon={<CalendarDays className="w-5 h-5 text-orange-400" />} />
                  <KpiCard label="% Vitória" value={winPct} icon={<TrendingUp className="w-5 h-5 text-green-500" />} />
                  <KpiCard label="Campeão" value={stats.wins} icon={<Trophy className="w-5 h-5 text-gold" />} />
                  <KpiCard label="Sessões" value={sessions} icon={<Star className="w-5 h-5 text-purple-400" />} />
                  <KpiCard label="Aproveitamento" value={champPct} icon={<TrendingUp className="w-5 h-5 text-green-400" />} />
                </div>
                <div className="grid grid-cols-3 lg:grid-cols-6 gap-4">
                  <KpiCard label="Gols" value={stats.goals} icon={<Target className="w-5 h-5 text-green-500" />} />
                  <KpiCard label="Gols/Sessão" value={goalsPer} icon={<Target className="w-5 h-5 text-green-400" />} />
                  <KpiCard label="Assistências" value={stats.assists} icon={<Handshake className="w-5 h-5 text-blue-400" />} />
                  <KpiCard label="Assists/Sessão" value={assistsPer} icon={<Handshake className="w-5 h-5 text-blue-300" />} />
                  <KpiCard label="Contribuições" value={contributions} icon={<Zap className="w-5 h-5 text-amber-400" />} />
                  <KpiCard label="Contrib/Sessão" value={contribPer} icon={<Zap className="w-5 h-5 text-amber-300" />} />
                </div>
              </>
            )
          })()}
        </section>

        {/* Awards section */}
        <section className="mb-10">
          <h2 className="text-xl font-semibold mb-4">Prêmios</h2>
          <Separator className="mb-6" />
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <KpiCard label="Bola Murcha" value={stats.wiltballWins ?? 0} icon={<Medal className="w-5 h-5 text-gold" />} />
            <KpiCard label="Artilheiro" value={stats.artilheiroWins ?? 0} icon={<Target className="w-5 h-5 text-red-500" />} />
            <KpiCard label="Garçom" value={stats.garcomWins ?? 0} icon={<Handshake className="w-5 h-5 text-blue-500" />} />
            <KpiCard label="Puskás" value={stats.puskasDates?.length ?? 0} icon={<Award className="w-5 h-5 text-orange-500" />} />
          </div>
        </section>

        {/* Stats Over Time */}
        <section className="mb-10">
          <h2 className="text-xl font-semibold mb-4">Evolução de Stats</h2>
          <Separator className="mb-6" />
          <div className="flex flex-col md:flex-row gap-6 md:items-start">
            {(stats.goals > 0 || stats.assists > 0) && (
              <ProfilePieChart goals={stats.goals} assists={stats.assists} />
            )}
            <StatsOverTimeChart timelinePoints={timelinePoints} loading={timelineLoading} />
          </div>
        </section>

        {/* Peladas section */}
        <section className="mb-10">
          <h2 className="text-xl font-semibold mb-4">{isOwnProfile ? 'Peladas' : 'Peladas em comum'}</h2>
          <Separator className="mb-6" />
          {peladasLoading ? (
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
              {[0, 1, 2].map((i) => (
                <div key={i} className="rounded-xl border overflow-hidden">
                  <Skeleton className="h-28 w-full rounded-none" />
                  <div className="p-3">
                    <Skeleton className="h-4 w-3/4" />
                  </div>
                </div>
              ))}
            </div>
          ) : profilePeladas.length === 0 ? (
            <p className="text-muted-foreground text-sm">
              {isOwnProfile ? 'Você ainda não está em nenhuma pelada.' : 'Vocês não têm peladas em comum.'}
            </p>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
              {profilePeladas.map((pelada) => (
                <Link
                  key={pelada.id}
                  to={`/pelada/${pelada.id}`}
                  className="rounded-xl border overflow-hidden hover:shadow-md transition-shadow block"
                >
                  {pelada.image ? (
                    <img
                      src={getFileUrl(pelada.image)}
                      alt={pelada.name}
                      className="h-28 w-full object-cover"
                    />
                  ) : (
                    <div className={`h-28 ${getPeladaGradient(pelada.name)} flex items-center justify-center`}>
                      <span className="text-2xl font-extrabold text-white tracking-wide select-none">
                        {getInitials(pelada.name)}
                      </span>
                    </div>
                  )}
                  <div className="p-3">
                    <p className="font-semibold text-sm leading-tight">{pelada.name}</p>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </section>

        {/* Match History */}
        <section className="mb-10">
          <h2 className="text-xl font-semibold mb-4">Histórico</h2>
          <Separator className="mb-6" />
          <MatchHistoryTable matchHistory={matchHistory} loading={matchHistoryLoading} />
        </section>
      </div>

      {editOpen && (
        <EditProfileModal
          profile={profile}
          onClose={() => setEditOpen(false)}
          onProfileUpdated={setProfile}
        />
      )}
    </div>
  )
}
