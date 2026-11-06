import { useCallback, useEffect, useState } from "react"
import { getDeletionBlockers } from "@/api/users"
import type { DeletionBlocker } from "@/types/user"

interface Loaded {
  attempt: number
  blockers: DeletionBlocker[] | null
  error: boolean
}

// What blocks deleting the account (peladas created, sole responsável financeiro). Results are keyed by attempt;
// a reload keeps the previous list on screen until it answers.
export function useDeletionBlockers() {
  const [attempt, setAttempt] = useState(0)
  const [loaded, setLoaded] = useState<Loaded | null>(null)

  useEffect(() => {
    let active = true
    getDeletionBlockers()
      .then((blockers) => active && setLoaded({ attempt, blockers, error: false }))
      .catch(() => active && setLoaded({ attempt, blockers: null, error: true }))
    return () => {
      active = false
    }
  }, [attempt])

  const reload = useCallback(() => setAttempt((value) => value + 1), [])
  const current = loaded?.attempt === attempt ? loaded : null

  return {
    blockers: loaded?.blockers ?? null,
    loading: loaded === null,
    error: current?.error ?? false,
    reload,
  }
}
