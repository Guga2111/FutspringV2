import { useCallback, useEffect, useState } from "react"
import { getPeladaAttendance } from "@/api/peladas"
import type { PeladaAttendanceDTO } from "@/types/pelada"

interface PeladaAttendanceState {
  data: PeladaAttendanceDTO | null
  loading: boolean
  error: boolean
  retry: () => void
}

interface LoadedResult {
  requestKey: string
  data: PeladaAttendanceDTO | null
  error: boolean
}

// Loads the pelada's attendance ranking; idle until `enabled` (the dialog's first open).
// Results are keyed by request, so another pelada's ranking never shows for this one.
export function usePeladaAttendance(peladaId: number, enabled: boolean): PeladaAttendanceState {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<LoadedResult | null>(null)
  const requestKey = `${peladaId}:${attempt}`

  useEffect(() => {
    if (!enabled) return

    let cancelled = false
    getPeladaAttendance(peladaId)
      .then((data) => {
        if (!cancelled) setResult({ requestKey, data, error: false })
      })
      .catch(() => {
        if (!cancelled) setResult({ requestKey, data: null, error: true })
      })

    return () => {
      cancelled = true
    }
  }, [peladaId, enabled, requestKey])

  const retry = useCallback(() => setAttempt((n) => n + 1), [])

  const current = result?.requestKey === requestKey ? result : null
  return {
    data: current?.data ?? null,
    loading: enabled && current === null,
    error: current?.error ?? false,
    retry,
  }
}
