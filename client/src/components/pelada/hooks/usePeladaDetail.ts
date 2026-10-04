import { useCallback, useEffect, useRef, useState } from "react"
import type { Dispatch, SetStateAction } from "react"
import { toast } from "sonner"
import { getPelada, getRanking, getPeladaAwards } from "@/api/peladas"
import { getDailiesForPelada } from "@/api/dailies"
import { getErrorStatus } from "@/lib/errors"
import type { PeladaDetail, PeladaAwards } from "@/types/pelada"
import type { DailyListItem, RankingDTO } from "@/types/daily"

// Results are stored with the pelada id they belong to, so navigating to another pelada shows the skeletons
// instead of the previous pelada's data
interface Keyed<T> {
  peladaId: number
  data: T
}

interface MainState {
  pelada: PeladaDetail | null
  accessDenied: boolean
  error: unknown
}

export function usePeladaDetail(id: string | undefined) {
  const peladaId = id ? Number(id) : null

  // The pelada on screen; a response for another pelada (a slow request after navigating) is dropped
  const currentId = useRef(peladaId)
  useEffect(() => {
    currentId.current = peladaId
  }, [peladaId])

  const [main, setMain] = useState<Keyed<MainState> | null>(null)
  const [dailies, setDailies] = useState<Keyed<DailyListItem[]> | null>(null)
  const [ranking, setRanking] = useState<Keyed<RankingDTO[]> | null>(null)
  const [awards, setAwards] = useState<Keyed<PeladaAwards | null> | null>(null)

  // Each fetch returns its promise so callers can await the reload
  const fetchPelada = useCallback(async () => {
    if (peladaId === null) return
    try {
      const pelada = await getPelada(peladaId)
      if (currentId.current !== peladaId) return
      setMain({ peladaId, data: { pelada, accessDenied: false, error: null } })
    } catch (err) {
      if (currentId.current !== peladaId) return
      if (getErrorStatus(err) === 403) {
        setMain({ peladaId, data: { pelada: null, accessDenied: true, error: null } })
      } else {
        // a failed reload keeps the pelada already on screen
        setMain((prev) => ({
          peladaId,
          data: { pelada: prev?.peladaId === peladaId ? prev.data.pelada : null, accessDenied: false, error: err },
        }))
        toast.error("Não foi possível carregar a pelada")
      }
    }
  }, [peladaId])

  const fetchList = useCallback(
    async <T,>(
      request: (peladaId: number) => Promise<T>,
      setter: Dispatch<SetStateAction<Keyed<T> | null>>,
      fallback: T,
      errorMessage: string,
    ) => {
      if (peladaId === null) return
      try {
        const data = await request(peladaId)
        if (currentId.current === peladaId) setter({ peladaId, data })
      } catch {
        if (currentId.current !== peladaId) return
        // a failed reload keeps the data already on screen
        setter((prev) => (prev?.peladaId === peladaId ? prev : { peladaId, data: fallback }))
        toast.error(errorMessage)
      }
    },
    [peladaId],
  )

  const fetchDailies = useCallback(
    () => fetchList(getDailiesForPelada, setDailies, [], "Não foi possível carregar as sessões"),
    [fetchList],
  )

  const fetchRanking = useCallback(
    () => fetchList(getRanking, setRanking, [], "Não foi possível carregar o ranking"),
    [fetchList],
  )

  const fetchAwards = useCallback(
    () => fetchList<PeladaAwards | null>(getPeladaAwards, setAwards, null, "Não foi possível carregar os prêmios"),
    [fetchList],
  )

  useEffect(() => {
    void fetchPelada()
  }, [fetchPelada])

  useEffect(() => {
    void fetchDailies()
  }, [fetchDailies])

  useEffect(() => {
    void fetchRanking()
  }, [fetchRanking])

  useEffect(() => {
    void fetchAwards()
  }, [fetchAwards])

  // Merge a mutation's result (confirm / unconfirm) into the sessions list
  const mergeDaily = useCallback(
    (item: DailyListItem) => {
      setDailies((prev) =>
        prev && prev.peladaId === peladaId
          ? { peladaId, data: prev.data.map((d) => (d.id === item.id ? { ...d, ...item } : d)) }
          : prev,
      )
    },
    [peladaId],
  )

  const refetch = useCallback(async () => {
    await Promise.all([fetchPelada(), fetchDailies(), fetchRanking(), fetchAwards()])
  }, [fetchPelada, fetchDailies, fetchRanking, fetchAwards])

  const current = <T,>(value: Keyed<T> | null) => (value !== null && value.peladaId === peladaId ? value : null)
  const mainState = current(main)?.data
  const dailiesState = current(dailies)
  const rankingState = current(ranking)
  const awardsState = current(awards)

  return {
    pelada: mainState?.pelada ?? null,
    dailies: dailiesState?.data ?? [],
    ranking: rankingState?.data ?? [],
    awards: awardsState?.data ?? null,
    loading: mainState === undefined,
    rankingLoading: rankingState === null,
    dailiesLoading: dailiesState === null,
    awardsLoading: awardsState === null,
    accessDenied: mainState?.accessDenied ?? false,
    error: mainState?.error ?? null,
    refetch,
    refetchPelada: fetchPelada,
    refetchDailies: fetchDailies,
    mergeDaily,
  }
}
