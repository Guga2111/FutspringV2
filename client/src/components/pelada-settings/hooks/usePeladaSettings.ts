import { useCallback, useEffect, useState } from "react"
import { getPelada } from "@/api/peladas"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { getErrorStatus } from "@/lib/errors"
import type { PeladaDetail, PeladaResponse } from "@/types/pelada"

interface LoadedResult {
  requestKey: string
  pelada: PeladaDetail | null
  accessDenied: boolean
  error: boolean
}

// The fields a PUT / image / transfer response shares with the detail (it has no members, creatorId or limit)
export function detailFields(response: PeladaResponse): Partial<PeladaDetail> {
  return {
    name: response.name,
    dayOfWeek: response.dayOfWeek,
    timeOfDay: response.timeOfDay,
    duration: response.duration,
    address: response.address,
    reference: response.reference,
    image: response.image,
    autoCreateDailyEnabled: response.autoCreateDailyEnabled,
    numberOfTeams: response.numberOfTeams,
    playersPerTeam: response.playersPerTeam,
    timeZone: response.timeZone,
  }
}

// The settings page's pelada. Results are keyed by request, so another pelada never shows for this one.
// `merge` applies a saved card to the page and reloads the sidebar's peladas (name, image, capacity).
export function usePeladaSettings(id: string | undefined) {
  const peladaId = id ? Number(id) : null
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<LoadedResult | null>(null)
  const requestKey = `${peladaId}:${attempt}`
  const { peladas, reload: reloadMyPeladas } = useMyPeladasContext()

  useEffect(() => {
    if (peladaId === null) return
    let cancelled = false
    getPelada(peladaId)
      .then((pelada) => {
        if (!cancelled) setResult({ requestKey, pelada, accessDenied: false, error: false })
      })
      .catch((err) => {
        if (cancelled) return
        const forbidden = getErrorStatus(err) === 403
        setResult({ requestKey, pelada: null, accessDenied: forbidden, error: !forbidden })
      })
    return () => {
      cancelled = true
    }
  }, [peladaId, requestKey])

  const merge = useCallback(
    (patch: Partial<PeladaDetail>) => {
      setResult((prev) => (prev?.pelada ? { ...prev, pelada: { ...prev.pelada, ...patch } } : prev))
      reloadMyPeladas()
    },
    [reloadMyPeladas],
  )

  const retry = useCallback(() => setAttempt((n) => n + 1), [])

  const current = result?.requestKey === requestKey ? result : null
  return {
    pelada: current?.pelada ?? null,
    // the next session from the sidebar's list, for the capacity warning
    nextDaily: peladas.find((p) => p.id === peladaId)?.nextDaily ?? null,
    loading: peladaId !== null && current === null,
    accessDenied: current?.accessDenied ?? false,
    error: current?.error ?? false,
    merge,
    retry,
  }
}
