import { useEffect, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { toast } from 'sonner'
import NavBar from '../components/NavBar'
import { getUserStats, getUser, updateUser, uploadUserImage, uploadBackgroundImage } from '../api/users'
import { getPeladaInitials, getPeladaGradient, getFileUrl } from '../lib/utils'
import { getMyPeladas, getPelada } from '../api/peladas'
import { useAuth } from '../hooks/useAuth'
import type { StatsDTO } from '../types/stats'
import type { ProfileDTO } from '../types/user'
import type { PeladaResponse } from '../types/pelada'
import { Skeleton } from '../components/ui/skeleton'
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
      <div className="px-6 lg:px-12">
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
    <div className="border border-t-2 border-t-green-500 rounded-lg p-4 flex flex-col items-center gap-1">
      <span className="text-2xl font-bold">{value}</span>
      <span className="text-sm text-muted-foreground">{label}</span>
    </div>
  )
}

const POSITIONS = ['GOALKEEPER', 'DEFENDER', 'MIDFIELDER', 'FORWARD'] as const

interface EditProfileModalProps {
  profile: ProfileDTO
  onClose: () => void
  onProfileUpdated: (updated: ProfileDTO) => void
}

function EditProfileModal({ profile, onClose, onProfileUpdated }: EditProfileModalProps) {
  const [username, setUsername] = useState(profile.username)
  const [position, setPosition] = useState(profile.position ?? '')
  const [stars, setStars] = useState(profile.stars)
  const [usernameError, setUsernameError] = useState('')
  const [saving, setSaving] = useState(false)
  const [avatarPreview, setAvatarPreview] = useState<string | null>(
    profile.image ? getFileUrl(profile.image)! : null,
  )
  const [bgPreview, setBgPreview] = useState<string | null>(
    profile.backgroundImage ? getFileUrl(profile.backgroundImage)! : null,
  )
  const avatarInputRef = useRef<HTMLInputElement>(null)
  const bgInputRef = useRef<HTMLInputElement>(null)

  const initials = profile.username
    .split(' ')
    .map((w) => w[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)

  async function handleAvatarChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const updated = await uploadUserImage(profile.id, file)
      setAvatarPreview(updated.image ? getFileUrl(updated.image)! : null)
      onProfileUpdated(updated)
    } catch {
      toast.error('Failed to upload avatar')
    }
    e.target.value = ''
  }

  async function handleBgChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const updated = await uploadBackgroundImage(profile.id, file)
      setBgPreview(updated.backgroundImage ? getFileUrl(updated.backgroundImage)! : null)
      onProfileUpdated(updated)
    } catch {
      toast.error('Failed to upload background image')
    }
    e.target.value = ''
  }

  async function handleSave() {
    setUsernameError('')
    setSaving(true)
    try {
      const updated = await updateUser(profile.id, {
        username,
        position,
        stars,
      })
      onProfileUpdated(updated)
      onClose()
      toast.success('Profile updated!')
    } catch (err: unknown) {
      const axiosErr = err as { response?: { status?: number } }
      if (axiosErr?.response?.status === 409) {
        setUsernameError('Username is already taken')
      } else {
        toast.error('Failed to update profile')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-background rounded-lg w-full max-w-md overflow-hidden">
        {/* Background image area */}
        <div
          className="w-full h-[120px] relative cursor-pointer group"
          style={
            bgPreview
              ? { backgroundImage: `url(${bgPreview})`, backgroundSize: 'cover', backgroundPosition: 'center' }
              : { background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }
          }
          onClick={() => bgInputRef.current?.click()}
        >
          <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
            <span className="text-white text-sm font-medium">Change background</span>
          </div>
        </div>
        <input ref={bgInputRef} type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={handleBgChange} />

        {/* Avatar */}
        <div className="px-6 -mt-10 mb-4">
          <div
            className="relative h-20 w-20 rounded-full border-4 border-background cursor-pointer group"
            onClick={() => avatarInputRef.current?.click()}
          >
            {avatarPreview ? (
              <img src={avatarPreview} alt={profile.username} className="h-full w-full rounded-full object-cover" />
            ) : (
              <div className="h-full w-full rounded-full bg-muted flex items-center justify-center text-lg font-bold">
                {initials}
              </div>
            )}
            <div className="absolute inset-0 rounded-full bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
              <span className="text-white text-xs font-medium">Edit</span>
            </div>
          </div>
          <input ref={avatarInputRef} type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={handleAvatarChange} />
        </div>

        {/* Form fields */}
        <div className="px-6 pb-6 space-y-4">
          <div>
            <label className="text-sm font-medium block mb-1">Username</label>
            <input
              type="text"
              value={username}
              onChange={(e) => { setUsername(e.target.value); setUsernameError('') }}
              minLength={3}
              maxLength={30}
              className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            />
            {usernameError && <p className="text-destructive text-xs mt-1">{usernameError}</p>}
          </div>

          <div>
            <label className="text-sm font-medium block mb-1">Position</label>
            <select
              value={position}
              onChange={(e) => setPosition(e.target.value)}
              className="w-full border rounded-md px-3 py-2 text-sm bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="">No position</option>
              {POSITIONS.map((p) => (
                <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="text-sm font-medium block mb-1">Stars</label>
            <div className="flex gap-1">
              {[1, 2, 3, 4, 5].map((i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => setStars(i)}
                  className="text-2xl text-yellow-400 hover:scale-110 transition-transform"
                >
                  {i <= stars ? '★' : '☆'}
                </button>
              ))}
            </div>
          </div>

          <div className="flex gap-3 pt-2">
            <button
              onClick={handleSave}
              disabled={saving}
              className="flex-1 px-4 py-2 bg-primary text-primary-foreground rounded-full text-sm font-medium hover:opacity-90 transition-opacity disabled:opacity-50"
            >
              {saving ? 'Saving…' : 'Save'}
            </button>
            <button
              onClick={onClose}
              className="flex-1 px-4 py-2 border rounded-full text-sm hover:bg-muted transition-colors"
            >
              Cancel
            </button>
          </div>
        </div>
      </div>
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
  const [profilePeladas, setProfilePeladas] = useState<PeladaResponse[]>([])
  const [peladasLoading, setPeladasLoading] = useState(true)

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
      <div
        className={`w-full h-[200px] relative ${!bgUrl ? 'bg-muted' : ''}`}
        style={bgUrl ? { backgroundImage: `url(${bgUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' } : undefined}
      />

      <div className="px-6 lg:px-12">
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
                  className="text-sm px-3 py-1 border rounded-full hover:bg-muted transition-colors"
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

        {/* Peladas section */}
        <div className="mb-8">
          <h2 className="text-lg font-semibold mb-4">Peladas</h2>
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
            <p className="text-muted-foreground text-sm">Not in any peladas yet.</p>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
              {profilePeladas.map((pelada) => (
                <Link
                  key={pelada.id}
                  to={`/pelada/${pelada.id}`}
                  className="rounded-xl border overflow-hidden hover:opacity-90 transition-opacity block"
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
        </div>
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
