import { useEffect, useState } from "react"
import { getFinanceCandidates } from "@/api/finance"
import type { FinanceCandidate } from "@/types/finance"
import { getErrorMessage } from "@/lib/errors"
export function useFinanceCandidates(peladaId: number, dailyId: number | null) {
  const [result, setResult] = useState<{ id: number; rows: FinanceCandidate[]; error: string | null } | null>(null)
  useEffect(() => {
    if (dailyId === null) return
    let cancelled = false
    getFinanceCandidates(peladaId, dailyId).then(rows => {
      if (!cancelled) setResult({ id: dailyId, rows, error: null })
    }).catch(e => {
      if (!cancelled) setResult({ id: dailyId, rows: [], error: getErrorMessage(e, "Não foi possível importar os participantes") })
    })
    return () => { cancelled = true }
  }, [peladaId, dailyId])
  return { rows: result?.id === dailyId ? result.rows : [], error: result?.id === dailyId ? result.error : null, loading: dailyId !== null && result?.id !== dailyId }
}
