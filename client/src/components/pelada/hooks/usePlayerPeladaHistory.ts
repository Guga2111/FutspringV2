import { useCallback, useEffect, useState } from "react"
import { getPlayerPeladaHistory } from "@/api/peladas"
import type { PlayerPeladaHistoryRow } from "@/types/pelada"

interface PlayerPeladaHistoryState {
  rows: PlayerPeladaHistoryRow[]
  totalSessions: number
  // true until the first response for this player arrives
  loading: boolean
  // true while a newer request (e.g. another period) is in flight; the previous rows stay on screen
  fetching: boolean
  error: boolean
  retry: () => void
}

interface LoadedResult {
  playerKey: string
  requestKey: string
  rows: PlayerPeladaHistoryRow[]
  totalSessions: number
  error: boolean
}

// Loads the last `limit` sessions (null = all) of one player in a pelada; idle while userId is null.
// Results are keyed by request, so another player's data never shows for this one.
export function usePlayerPeladaHistory(
  peladaId: number,
  userId: number | null,
  limit: number | null
): PlayerPeladaHistoryState {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<LoadedResult | null>(null)
  const playerKey = `${peladaId}:${userId}`
  const requestKey = `${playerKey}:${limit}:${attempt}`

  useEffect(() => {
    if (userId === null) return

    let cancelled = false
    getPlayerPeladaHistory(peladaId, userId, limit)
      .then((history) => {
        if (cancelled) return
        setResult({
          playerKey,
          requestKey,
          rows: history.rows,
          totalSessions: history.totalSessions,
          error: false,
        })
      })
      .catch(() => {
        if (cancelled) return
        setResult({ playerKey, requestKey, rows: [], totalSessions: 0, error: true })
      })

    return () => {
      cancelled = true
    }
  }, [peladaId, userId, limit, playerKey, requestKey])

  const retry = useCallback(() => setAttempt((n) => n + 1), [])

  // A failed result only counts for its own request: a retry or another period after an error
  // shows the skeleton again instead of an empty "previous" result
  const current =
    result?.playerKey === playerKey && (!result.error || result.requestKey === requestKey)
      ? result
      : null
  return {
    rows: current?.rows ?? [],
    totalSessions: current?.totalSessions ?? 0,
    loading: userId !== null && current === null,
    fetching: current !== null && current.requestKey !== requestKey,
    error: current?.requestKey === requestKey && current.error,
    retry,
  }
}
