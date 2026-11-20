import { useEffect, useState } from "react"
import { getUser, getUserMatchHistory, getUserPeladas, getUserStats, getUserStatsTimeline } from "@/api/users"
import { getErrorStatus } from "@/lib/errors"
import { useReload } from "@/hooks/useReload"
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
  // reload() (pull to refresh) resolves when the four reads answered. A reload keeps what is on screen: a failed
  // read only replaces data of another user (or none)
  const { version, reload, settle } = useReload()

  useEffect(() => {
    let cancelled = false
    const set = <T,>(setter: (value: Keyed<T>) => void, data: T) => !cancelled && setter({ userId, data })
    const keep = <T,>(setter: (update: (prev: Keyed<T> | null) => Keyed<T> | null) => void, fallback: T) =>
      !cancelled && setter((prev) => (prev?.userId === userId ? prev : { userId, data: fallback }))

    const main = Promise.all([getUser(userId), getUserStats(userId)])
      .then(([profile, stats]) => set(setMain, { status: "ready" as ProfileStatus, profile, stats }))
      .catch((error) => {
        const status = getErrorStatus(error)
        const failed = {
          status: (status === 403 ? "forbidden" : status === 404 ? "notFound" : "error") as ProfileStatus,
          profile: null,
          stats: null,
        }
        // a failed reload keeps the profile on screen (losing access, 403/404, still shows)
        if (!cancelled) setMain((prev) => (prev?.userId === userId && prev.data.status === "ready" && failed.status === "error" ? prev : { userId, data: failed }))
      })

    const { from, to } = lastThreeMonths()
    Promise.all([
      main,
      getUserPeladas(userId).then((data) => set(setPeladas, data)).catch(() => keep(setPeladas, [])),
      getUserStatsTimeline(userId, from, to).then((data) => set(setTimeline, data.points)).catch(() => keep(setTimeline, [])),
      getUserMatchHistory(userId).then((data) => set(setHistory, data.rows)).catch(() => keep(setHistory, [])),
    ]).finally(() => !cancelled && settle())

    return () => {
      cancelled = true
    }
  }, [userId, version, settle])

  const current = <T,>(value: Keyed<T> | null) => (value?.userId === userId ? value.data : null)
  const mainData = current(main)

  return {
    status: mainData?.status ?? "loading",
    profile: mainData?.profile ?? null,
    stats: mainData?.stats ?? null,
    peladas: current(peladas) ?? [],
    peladasLoading: current(peladas) === null,
    timelinePoints: current(timeline) ?? [],
    timelineLoading: current(timeline) === null,
    matchHistory: current(history) ?? [],
    matchHistoryLoading: current(history) === null,
    reload,
  }
}
