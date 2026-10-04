import { useCallback, useEffect, useState } from "react"
import { getMyPeladas } from "@/api/peladas"
import type { PeladaResponse } from "@/types/pelada"

// The user's peladas. Each item already carries memberCount, isAdmin and nextDaily, so there is one request.
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

  return { peladas, loading, error, reload }
}
