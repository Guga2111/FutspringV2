import { useEffect, useState } from "react"
import { getFinanceAudit, getFinanceMovements } from "@/api/finance"
import type { FinancePage, FinanceAudit, FinanceMovement } from "@/types/finance"
import { getErrorMessage } from "@/lib/errors"
export function useFinanceHistory(peladaId: number, kind: "cash" | "audit", revision: number) {
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<{ key: string; data: FinancePage<FinanceMovement> | FinancePage<FinanceAudit>; error: string | null } | null>(null)
  const key = `${peladaId}:${kind}:${revision}:${page}:${attempt}`
  useEffect(() => {
    let cancelled = false
    const request = kind === "cash" ? getFinanceMovements(peladaId, page) : getFinanceAudit(peladaId, page)
    request.then(data => { if (!cancelled) setResult({ key, data, error: null }) }).catch(e => {
      if (!cancelled) setResult({ key, data: { items: [], hasMore: false }, error: getErrorMessage(e, "Não foi possível carregar o histórico") })
    })
    return () => { cancelled = true }
  }, [peladaId, kind, page, key])
  return { data: result?.key === key ? result.data : null, error: result?.key === key ? result.error : null, page, setPage, retry: () => setAttempt(v => v + 1) }
}
