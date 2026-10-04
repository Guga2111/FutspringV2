import { useEffect, useState } from "react"
import { getUser, getUserMatchHistory, getUserPeladas, getUserStats, getUserStatsTimeline } from "@/api/users"
import { getErrorStatus } from "@/lib/errors"
import type { PeladaResponse } from "@/types/pelada"
import type { MatchHistoryRow, StatsDTO, TimelinePoint } from "@/types/stats"
import type { ProfileDTO } from "@/types/user"

// Results are stored with the user id they belong to, so switching profiles never shows the previous user's data
interface Keyed<T> {
  userId: number
  data: T
}

type ProfileStatus = "loading" | "ready" | "forbidden" | "notFound" | "error"

function lastThreeMonths(): { from: string; to: string } {
  const today = new Date()
  const fromDate = new Date(today)
  fromDate.setMonth(fromDate.getMonth() - 3)
  return { from: fromDate.toISOString().slice(0, 10), to: today.toISOString().slice(0, 10) }
}

// Profile page data. The profile and stats load first; peladas, timeline and history load in parallel
// with their own loading flags. Stats are only visible to the owner and to players who share a pelada (403 otherwise).
export function useProfile(userId: number) {
  const [main, setMain] = useState<Keyed<{ status: ProfileStatus; profile: ProfileDTO | null; stats: StatsDTO | null }> | null>(null)
  const [peladas, setPeladas] = useState<Keyed<PeladaResponse[]> | null>(null)
  const [timeline, setTimeline] = useState<Keyed<TimelinePoint[]> | null>(null)
  const [history, setHistory] = useState<Keyed<MatchHistoryRow[]> | null>(null)

  useEffect(() => {
    let cancelled = false
    const set = <T,>(setter: (value: Keyed<T>) => void, data: T) => !cancelled && setter({ userId, data })

    Promise.all([getUser(userId), getUserStats(userId)])
      .then(([profile, stats]) => set(setMain, { status: "ready" as ProfileStatus, profile, stats }))
      .catch((error) => {
        const status = getErrorStatus(error)
        set(setMain, {
          status: status === 403 ? "forbidden" : status === 404 ? "notFound" : "error",
          profile: null,
          stats: null,
        })
      })

    getUserPeladas(userId).then((data) => set(setPeladas, data)).catch(() => set(setPeladas, []))
    const { from, to } = lastThreeMonths()
    getUserStatsTimeline(userId, from, to).then((data) => set(setTimeline, data.points)).catch(() => set(setTimeline, []))
    getUserMatchHistory(userId).then((data) => set(setHistory, data.rows)).catch(() => set(setHistory, []))

    return () => {
      cancelled = true
    }
  }, [userId])

  const current = <T,>(value: Keyed<T> | null) => (value?.userId === userId ? value.data : null)
  const mainData = current(main)

  return {
    status: mainData?.status ?? "loading",
    profile: mainData?.profile ?? null,
    stats: mainData?.stats ?? null,
    // owner edits merge the returned profile
    setProfile: (profile: ProfileDTO) =>
      setMain((prev) => (prev && prev.userId === userId ? { ...prev, data: { ...prev.data, profile } } : prev)),
    peladas: current(peladas) ?? [],
    peladasLoading: current(peladas) === null,
    timelinePoints: current(timeline) ?? [],
    timelineLoading: current(timeline) === null,
    matchHistory: current(history) ?? [],
    matchHistoryLoading: current(history) === null,
  }
}
