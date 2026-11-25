import { useEffect, useState } from "react"
import { getUserStats } from "@/api/users"
import { useReload } from "@/hooks/useReload"
import type { StatsDTO } from "@/types/stats"

interface Result {
  userId: number
  stats: StatsDTO | null
  error: boolean
}

// The user's totals over every pelada (Home "Seus números"); results keyed by user id
export function useUserStats(userId: number | null) {
  const [result, setResult] = useState<Result | null>(null)
  // reload() resolves when the totals answered; a failed reload keeps the totals on screen
  const { version, reload, settle } = useReload()

  useEffect(() => {
    if (userId == null) return
    let cancelled = false
    getUserStats(userId)
      .then((stats) => !cancelled && setResult({ userId, stats, error: false }))
      .catch(() => !cancelled && setResult((prev) => (prev?.userId === userId && prev.stats ? prev : { userId, stats: null, error: true })))
      .finally(() => !cancelled && settle())
    return () => {
      cancelled = true
    }
  }, [userId, version, settle])

  const current = result?.userId === userId ? result : null
  return { stats: current?.stats ?? null, loading: userId != null && current === null, error: current?.error ?? false, reload }
}
