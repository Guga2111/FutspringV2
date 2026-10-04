import { useCallback, useEffect, useState } from "react"
import { getMyPeladas } from "@/api/peladas"
import type { NextDaily, PeladaResponse } from "@/types/pelada"

// The user's peladas. Each item already carries memberCount, isAdmin and nextDaily, so there is one request.
// Used once by AppLayout and shared through MyPeladasContext.
export function useMyPeladas() {
  const [peladas, setPeladas] = useState<PeladaResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let cancelled = false
    getMyPeladas()
      .then((data) => {
        if (cancelled) return
        setPeladas(data)
        setError(false)
      })
      .catch(() => !cancelled && setError(true))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])

  const updateNextDaily = useCallback((peladaId: number, patch: Partial<NextDaily>) => {
    setPeladas((prev) =>
      prev.map((p) => (p.id === peladaId && p.nextDaily ? { ...p, nextDaily: { ...p.nextDaily, ...patch } } : p)),
    )
  }, [])

  return { peladas, loading, error, reload, updateNextDaily }
}
