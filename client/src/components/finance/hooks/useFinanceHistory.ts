import { useEffect, useState } from "react"
import { getFinanceAudit, getFinanceMovements, type FinanceHistoryFilter } from "@/api/finance"
import type { FinancePage, FinanceAudit, FinanceMovement } from "@/types/finance"
import { getErrorMessage } from "@/lib/errors"

// One numbered page of the cash statement or the audit, filtered on the server. The page belongs to the filter
// it was picked under, so a new filter starts at page 0 without a reset effect.
export function useFinanceHistory(peladaId: number, kind: "cash" | "audit", revision: number, filter: FinanceHistoryFilter) {
  const filterKey = JSON.stringify(kind === "cash" ? { from: filter.from, to: filter.to } : filter)
  const [paging, setPaging] = useState({ filterKey, page: 0 })
  const page = paging.filterKey === filterKey ? paging.page : 0
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<{ key: string; data: FinancePage<FinanceMovement> | FinancePage<FinanceAudit>; error: string | null } | null>(null)
  const key = `${peladaId}:${kind}:${revision}:${page}:${attempt}:${filterKey}`
  useEffect(() => {
    let cancelled = false
    const parsed = JSON.parse(filterKey) as FinanceHistoryFilter
    const request = kind === "cash" ? getFinanceMovements(peladaId, page, parsed) : getFinanceAudit(peladaId, page, parsed)
    request.then(data => { if (!cancelled) setResult({ key, data, error: null }) }).catch(e => {
      if (!cancelled) setResult({ key, data: { items: [], hasMore: false, total: 0 }, error: getErrorMessage(e, "Não foi possível carregar o histórico") })
    })
    return () => { cancelled = true }
  }, [peladaId, kind, page, filterKey, key])
  return {
    data: result?.key === key ? result.data : null,
    error: result?.key === key ? result.error : null,
    page,
    setPage: (next: number) => setPaging({ filterKey, page: next }),
    retry: () => setAttempt(v => v + 1),
  }
}
