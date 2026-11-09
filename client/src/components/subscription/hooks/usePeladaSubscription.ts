import { useCallback, useEffect, useState } from "react"
import { getPeladaSubscription } from "@/api/subscription"
import { getErrorStatus } from "@/lib/errors"
import type { PeladaSubscription } from "@/types/subscription"

interface Result {
  key: string
  data: PeladaSubscription | null
  accessDenied: boolean
  error: boolean
}

// The creator's view of the pelada's subscription; idle while `enabled` is false (anyone but the creator gets 403).
// Results are keyed by pelada and attempt, so another pelada never shows this one's state.
export function usePeladaSubscription(peladaId: number, enabled: boolean) {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<Result | null>(null)
  const key = `${peladaId}:${attempt}`

  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    getPeladaSubscription(peladaId)
      .then((data) => {
        if (!cancelled) setResult({ key, data, accessDenied: false, error: false })
      })
      .catch((error) => {
        if (cancelled) return
        setResult({ key, data: null, accessDenied: getErrorStatus(error) === 403, error: true })
      })
    return () => {
      cancelled = true
    }
  }, [peladaId, enabled, key])

  const current = result?.key === key ? result : null
  // the claim answers the whole view, which replaces the one on screen
  const setData = useCallback((data: PeladaSubscription) => {
    setResult((prev) => (prev ? { ...prev, data } : prev))
  }, [])

  return {
    data: current?.data ?? null,
    loading: enabled && current === null,
    accessDenied: current?.accessDenied ?? false,
    error: current !== null && current.error && !current.accessDenied,
    retry: () => setAttempt((n) => n + 1),
    setData,
  }
}
