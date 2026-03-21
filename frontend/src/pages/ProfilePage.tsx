import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { toast } from 'sonner'
import NavBar from '../components/NavBar'
import { getUserStats } from '../api/users'
import type { StatsDTO } from '../types/stats'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'

function SkeletonBlock({ className }: { className: string }) {
  return <div className={`bg-muted rounded animate-pulse ${className}`} />
}

function ProfileSkeleton() {
  return (
    <div className="container max-w-2xl mx-auto px-4 py-6">
      <SkeletonBlock className="h-8 w-48 mb-6" />
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        {[0, 1, 2, 3].map((i) => (
          <SkeletonBlock key={i} className="h-24 w-full" />
        ))}
      </div>
      <SkeletonBlock className="h-64 w-full" />
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
  const [stats, setStats] = useState<StatsDTO | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!id) return
    getUserStats(Number(id))
      .then(setStats)
      .catch(() => toast.error('Failed to load profile stats'))
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

  if (!stats) {
    return (
      <>
        <NavBar />
        <div className="flex items-center justify-center min-h-[60vh]">
          <p className="text-muted-foreground">Profile not found.</p>
        </div>
      </>
    )
  }

  const chartData = [
    { name: stats.username, Goals: stats.goals, Assists: stats.assists },
  ]

  return (
    <>
      <NavBar />
      <div className="container max-w-2xl mx-auto px-4 py-6">
        <h1 className="text-2xl font-bold mb-6">{stats.username}</h1>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
          <StatCard label="Goals" value={stats.goals} />
          <StatCard label="Assists" value={stats.assists} />
          <StatCard label="Matches" value={stats.matchesPlayed} />
          <StatCard label="Wins" value={stats.wins} />
        </div>

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
      </div>
    </>
  )
}
