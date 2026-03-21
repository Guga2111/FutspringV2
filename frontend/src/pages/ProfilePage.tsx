import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { toast } from 'sonner'
import NavBar from '../components/NavBar'
import { getUserStats, getUser } from '../api/users'
import { useAuth } from '../hooks/useAuth'
import type { StatsDTO } from '../types/stats'
import type { ProfileDTO } from '../types/user'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'

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

function Stars({ count }: { count: number }) {
  return (
    <span className="text-yellow-400 text-lg">
      {[1, 2, 3, 4, 5].map((i) => (
        <span key={i}>{i <= count ? '★' : '☆'}</span>
      ))}
    </span>
  )
}

function SkeletonBlock({ className }: { className: string }) {
  return <div className={`bg-muted rounded animate-pulse ${className}`} />
}

function ProfileSkeleton() {
  return (
    <div>
      <SkeletonBlock className="w-full h-[200px]" />
      <div className="container max-w-2xl mx-auto px-4">
        <div className="flex items-end gap-4 -mt-12 mb-6">
          <SkeletonBlock className="h-24 w-24 rounded-full border-4 border-background" />
          <div className="flex-1 pb-2 space-y-2">
            <SkeletonBlock className="h-6 w-40" />
            <SkeletonBlock className="h-5 w-24" />
          </div>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
          {[0, 1, 2, 3].map((i) => (
            <SkeletonBlock key={i} className="h-24 w-full" />
          ))}
        </div>
        <SkeletonBlock className="h-64 w-full mb-8" />
        <SkeletonBlock className="h-40 w-full" />
      </div>
    </div>
  )
}

interface StatCardProps {
  label: string
  value: number
}

function StatCard({ label, value }: StatCardProps) {
  return (
    <div className="border rounded-lg p-4 flex flex-col items-center gap-1">
      <span className="text-2xl font-bold">{value}</span>
      <span className="text-sm text-muted-foreground">{label}</span>
    </div>
  )
}

export default function ProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { user: currentUser } = useAuth()
  const [profile, setProfile] = useState<ProfileDTO | null>(null)
  const [stats, setStats] = useState<StatsDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [editOpen, setEditOpen] = useState(false)

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
          <p className="text-muted-foreground">Profile not found.</p>
        </div>
      </>
    )
  }

  const isOwnProfile = currentUser?.id === profile.id
  const chartData = [{ name: stats.username, Goals: stats.goals, Assists: stats.assists }]
  const avatarUrl = profile.image ? `/api/v1/files/${profile.image}` : null
  const bgUrl = profile.backgroundImage ? `/api/v1/files/${profile.backgroundImage}` : null

  const initials = profile.username
    .split(' ')
    .map((w) => w[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)

  return (
    <>
      <NavBar />

      {/* Background banner */}
      <div
        className="w-full h-[200px] relative"
        style={
          bgUrl
            ? { backgroundImage: `url(${bgUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' }
            : { background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }
        }
      />

      <div className="container max-w-2xl mx-auto px-4">
        {/* Avatar + name row */}
        <div className="flex items-end gap-4 -mt-12 mb-6">
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
                  className="text-sm px-3 py-1 border rounded-md hover:bg-muted transition-colors"
                >
                  Edit Profile
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

        {/* Stats cards */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
          <StatCard label="Goals" value={stats.goals} />
          <StatCard label="Assists" value={stats.assists} />
          <StatCard label="Matches" value={stats.matchesPlayed} />
          <StatCard label="Wins" value={stats.wins} />
        </div>

        {/* Goals & Assists chart */}
        <div className="border rounded-lg p-4 mb-8">
          <h2 className="text-lg font-semibold mb-4">Goals & Assists</h2>
          {stats.goals === 0 && stats.assists === 0 ? (
            <p className="text-muted-foreground text-sm">No stats recorded yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Legend />
                <Bar dataKey="Goals" fill="#3b82f6" />
                <Bar dataKey="Assists" fill="#22c55e" />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Puskas Award History */}
        <div className="border rounded-lg p-4 mb-8">
          <h2 className="text-lg font-semibold mb-4">Puskas Award History</h2>
          {stats.puskasDates.length === 0 ? (
            <p className="text-muted-foreground text-sm">No Puskas wins yet.</p>
          ) : (
            <ul className="space-y-2">
              {[...stats.puskasDates]
                .sort((a, b) => (a > b ? -1 : 1))
                .map((dateStr, i) => (
                  <li key={i} className="text-sm">
                    {new Date(`${dateStr}T12:00:00`).toLocaleDateString('en-US', {
                      year: 'numeric',
                      month: 'long',
                      day: 'numeric',
                    })}
                  </li>
                ))}
            </ul>
          )}
        </div>
      </div>

      {/* Edit modal placeholder — implemented in US-004 */}
      {editOpen && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-background rounded-lg p-6 w-full max-w-md">
            <p className="text-muted-foreground">Edit profile coming soon.</p>
            <button
              onClick={() => setEditOpen(false)}
              className="mt-4 px-4 py-2 border rounded-md hover:bg-muted transition-colors"
            >
              Close
            </button>
          </div>
        </div>
      )}
    </>
  )
}
