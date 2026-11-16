import { useCallback, useEffect, useState } from "react"
import { getMyPeladas } from "@/api/peladas"
import { useReload } from "@/hooks/useReload"
import type { NextDaily, PeladaResponse } from "@/types/pelada"

// The user's peladas. Each item already carries memberCount, isAdmin, isFinanceManager and nextDaily, so there is
// one request. Used once by AppLayout and shared through MyPeladasContext.
export function useMyPeladas() {
  const [peladas, setPeladas] = useState<PeladaResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  // reload() resolves when the new list answered (a failure shows in `error`, the list on screen stays)
  const { version, reload, settle } = useReload()

  useEffect(() => {
    let cancelled = false
    getMyPeladas()
      .then((data) => {
        if (cancelled) return
        setPeladas(data)
        setError(false)
      })
      .catch(() => !cancelled && setError(true))
      .finally(() => {
        if (cancelled) return
        setLoading(false)
        settle()
      })
    return () => {
      cancelled = true
    }
  }, [version, settle])

  const updateNextDaily = useCallback((peladaId: number, patch: Partial<NextDaily>) => {
    setPeladas((prev) =>
      prev.map((p) => (p.id === peladaId && p.nextDaily ? { ...p, nextDaily: { ...p.nextDaily, ...patch } } : p)),
    )
  }, [])

  return { peladas, loading, error, reload, updateNextDaily }
}
