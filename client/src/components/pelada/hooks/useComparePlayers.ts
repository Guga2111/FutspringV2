import { useEffect, useState } from "react"
import { getPlayerPeladaStats } from "@/api/peladas"
import { getUser } from "@/api/users"
import type { PlayerCompareData } from "@/components/pelada/ComparablePlayerStatsCard"

interface Result {
  key: string
  playerA: PlayerCompareData | null
  playerB: PlayerCompareData | null
}

// Profile + pelada stats of two players. Results are keyed by the pair, so changing a player never shows
// the previous pair's data and no state is reset inside the effect.
export function useComparePlayers(peladaId: number, playerAId: number | null, playerBId: number | null) {
  const key = playerAId !== null && playerBId !== null ? `${peladaId}:${playerAId}:${playerBId}` : null
  const [result, setResult] = useState<Result | null>(null)

  useEffect(() => {
    if (key === null || playerAId === null || playerBId === null) return
    let cancelled = false
    Promise.all([
      getUser(playerAId),
      getPlayerPeladaStats(peladaId, playerAId),
      getUser(playerBId),
      getPlayerPeladaStats(peladaId, playerBId),
    ])
      .then(([profileA, statsA, profileB, statsB]) => {
        if (!cancelled) {
          setResult({ key, playerA: { profile: profileA, stats: statsA }, playerB: { profile: profileB, stats: statsB } })
        }
      })
      .catch(() => {
        // the user can pick another pair
        if (!cancelled) setResult({ key, playerA: null, playerB: null })
      })
    return () => {
      cancelled = true
    }
  }, [key, peladaId, playerAId, playerBId])

  const current = key !== null && result?.key === key ? result : null
  return {
    loading: key !== null && current === null,
    playerA: current?.playerA ?? null,
    playerB: current?.playerB ?? null,
  }
}
