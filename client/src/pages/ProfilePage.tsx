import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { toast } from 'sonner'
import NavBar from '../components/NavBar'
import { getUserStats, getUser, getUserMatchHistory, getUserStatsTimeline } from '../api/users'
import { getPeladaInitials, getPeladaGradient, getFileUrl } from '../lib/utils'
import { getMyPeladas, getPelada } from '../api/peladas'
import { useAuth } from '../hooks/useAuth'
import type { StatsDTO } from '../types/stats'
import type { ProfileDTO } from '../types/user'
import type { PeladaResponse } from '../types/pelada'
import type { TimelinePoint, MatchHistoryRow } from '../types/stats'
import { Skeleton } from '../components/ui/skeleton'
import { Separator } from '../components/ui/separator'
import { Target, Handshake, CalendarDays, Trophy, TrendingUp, Medal, Star, Swords, Award, Zap } from 'lucide-react'
import KpiCard from '../components/profile/KpiCard'
import ProfilePieChart from '../components/profile/ProfilePieChart'
import StatsOverTimeChart from '../components/profile/StatsOverTimeChart'
import MatchHistoryTable from '../components/profile/MatchHistoryTable'
import EditProfileModal from '../components/profile/EditProfileModal'
import { Stars, ProfileSkeleton } from '../components/profile'

const POSITION_COLORS: Record<string, string> = {
  GOALKEEPER: 'bg-green-100 text-green-800',
  DEFENDER: 'bg-blue-100 text-blue-800',
  MIDFIELDER: 'bg-yellow-100 text-yellow-800',
  FORWARD: 'bg-red-100 text-red-800',
}

const POSITION_LABELS: Record<string, string> = {
  GOALKEEPER: 'GK',
  DEFENDER: 'DEF',
  MIDFIELDER: 'MID',
  FORWARD: 'FWD',
}

export default function ProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { user: currentUser } = useAuth()
  const [profile, setProfile] = useState<ProfileDTO | null>(null)
  const [stats, setStats] = useState<StatsDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [editOpen, setEditOpen] = useState(false)
  const [profilePeladas, setProfilePeladas] = useState<PeladaResponse[]>([])
  const [peladasLoading, setPeladasLoading] = useState(true)
  const [timelinePoints, setTimelinePoints] = useState<TimelinePoint[]>([])
  const [timelineLoading, setTimelineLoading] = useState(true)
  const [matchHistory, setMatchHistory] = useState<MatchHistoryRow[]>([])
  const [matchHistoryLoading, setMatchHistoryLoading] = useState(true)

  useEffect(() => {
    if (!id) return
    const userId = Number(id)
    Promise.all([getUser(userId), getUserStats(userId)])
      .then(([profileData, statsData]) => {
        setProfile(profileData)
        setStats(statsData)
      })
      .catch(() => toast.error('Failed to load profile'))
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    if (!id) return
    const userId = Number(id)
    setPeladasLoading(true)
    getMyPeladas()
      .then((myPeladas) =>
        Promise.all(myPeladas.map((p) => getPelada(p.id))).then((details) =>
          details
            .filter((d) => d.members.some((m) => m.id === userId))
            .map((d) => myPeladas.find((p) => p.id === d.id)!)
        )
      )
      .then(setProfilePeladas)
      .catch(() => toast.error('Failed to load peladas'))
      .finally(() => setPeladasLoading(false))
  }, [id])

  useEffect(() => {
    if (!id) return
    const userId = Number(id)
    setTimelineLoading(true)
    const today = new Date()
    const to = today.toISOString().slice(0, 10)
    const fromDate = new Date(today)
    fromDate.setMonth(fromDate.getMonth() - 3)
    const from = fromDate.toISOString().slice(0, 10)
    getUserStatsTimeline(userId, from, to)
      .then((data) => setTimelinePoints(data.points))
      .catch(() => toast.error('Failed to load stats timeline'))
      .finally(() => setTimelineLoading(false))
  }, [id])

  useEffect(() => {
    if (!id) return
    const userId = Number(id)
    setMatchHistoryLoading(true)
    getUserMatchHistory(userId)
      .then((data) => setMatchHistory(data.rows))
      .catch(() => toast.error('Failed to load match history'))
      .finally(() => setMatchHistoryLoading(false))
  }, [id])

  if (loading) {
    return (
      <>
        <NavBar />
        <ProfileSkeleton />
      </>
    )
  }

  if (!profile || !stats) {
    return (
      <>
        <NavBar />
        <div className="flex items-center justify-center min-h-[60vh]">
          <p className="text-muted-foreground">Perfil não encontrado.</p>
        </div>
      </>
    )
  }

  const isOwnProfile = currentUser?.id === profile.id
  const avatarUrl = profile.image ? getFileUrl(profile.image)! : null
  const bgUrl = profile.backgroundImage ? getFileUrl(profile.backgroundImage)! : null

  const initials = profile.username
    .split(' ')
    .map((w) => w[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)

  return (
    <div className="page-enter">
      <NavBar />

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
          {avatarUrl ? (
            <img
              src={avatarUrl}
              alt={profile.username}
              className="h-24 w-24 rounded-full border-4 border-background object-cover"
            />
          ) : (
            <div className="h-24 w-24 rounded-full border-4 border-background bg-muted flex items-center justify-center text-xl font-bold">
              {initials}
            </div>
          )}
          <div className="flex-1 pb-2">
            <div className="flex items-center gap-3 flex-wrap">
              <h1 className="text-2xl font-bold">{profile.username}</h1>
              {isOwnProfile && (
                <button
                  onClick={() => setEditOpen(true)}
                  className="text-sm px-3 py-1 border rounded-full hover:bg-muted transition-colors"
                >
                  Editar Perfil
                </button>
              )}
            </div>
            <div className="flex items-center gap-3 mt-1 flex-wrap">
              {profile.position && (
                <span
                  className={`text-xs font-semibold px-2 py-0.5 rounded ${POSITION_COLORS[profile.position] ?? 'bg-muted text-muted-foreground'}`}
                >
                  {POSITION_LABELS[profile.position] ?? profile.position}
                </span>
              )}
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
                  <KpiCard label="Campeão" value={stats.wins} icon={<Trophy className="w-5 h-5 text-yellow-400" />} />
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
            <KpiCard label="Witball" value={stats.wiltballWins ?? 0} icon={<Medal className="w-5 h-5 text-yellow-500" />} />
            <KpiCard label="Artilheiro" value={stats.artilheiroWins ?? 0} icon={<Target className="w-5 h-5 text-red-500" />} />
            <KpiCard label="Garçom" value={stats.garcomWins ?? 0} icon={<Handshake className="w-5 h-5 text-blue-500" />} />
            <KpiCard label="Puskas" value={stats.puskasDates?.length ?? 0} icon={<Award className="w-5 h-5 text-orange-500" />} />
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
          <h2 className="text-xl font-semibold mb-4">Peladas</h2>
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
            <p className="text-muted-foreground text-sm">Ainda não está em nenhuma pelada.</p>
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
                        {getPeladaInitials(pelada.name)}
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
          onProfileUpdated={(updated) => setProfile(updated)}
        />
      )}
    </div>
  )
}
