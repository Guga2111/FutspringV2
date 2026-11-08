import { useCallback, useEffect, useRef, useState } from "react"
import * as api from "@/api/finance"
import type { FinanceCommand } from "@/schemas/finance"
import type { FinanceOverview, FinanceDetail, FinanceResult, FinanceListScope } from "@/types/finance"
import { clampFinancePage } from "@/utils/finance"
import { getErrorMessage, getErrorStatus } from "@/lib/errors"

// selected: the open collection, read from the URL (/financeiro/:listId); select navigates there.
// writable: false while the pelada is read-only (subscription); `locked` disables every write control
export function useFinance(peladaId: number, selected: number | null, select: (id: number | null) => void, writable = true) {
  const [attempt, setAttempt] = useState(0)
  // the list section (open / closed) and its numbered page
  const [scope, setScopeState] = useState<FinanceListScope>("ACTIVE")
  const [page, setPage] = useState(0)
  const [overview, setOverview] = useState<FinanceOverview | null>(null)
  const [error, setError] = useState<string | null>(null)
  // keyed by collection id: opening another collection shows the skeleton, never the previous one
  const [detailState, setDetailState] = useState<{ id: number; data: FinanceDetail | null; error: string | null } | null>(null)
  const detail = detailState?.id === selected ? detailState.data : null
  const detailError = detailState?.id === selected ? detailState.error : null
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const generation = useRef(0)
  const detailGeneration = useRef(0)
  const retryCommand = useRef<{ signature: string; command: FinanceCommand } | null>(null)
  const refresh = useCallback(() => setAttempt(v => v + 1), [])
  useEffect(() => {
    let cancelled = false
    const request = ++generation.current
    api.getFinance(peladaId, scope, page).then(data => {
      if (cancelled || generation.current !== request) return
      const last = clampFinancePage(page, data.total, api.FINANCE_LISTS_PAGE_SIZE)
      if (last !== page) { setPage(last); return }
      setOverview(data); setError(null)
    }).catch(e => {
      if (!cancelled && generation.current === request) setError(getErrorMessage(e, "Não foi possível carregar o financeiro"))
    })
    return () => { cancelled = true }
  }, [peladaId, scope, page, attempt])
  useEffect(() => {
    if (selected === null) return
    let cancelled = false
    const request = ++detailGeneration.current
    api.getFinanceList(peladaId, selected).then(data => {
      if (!cancelled && detailGeneration.current === request) setDetailState({ id: selected, data, error: null })
    }).catch(e => {
      if (!cancelled && detailGeneration.current === request) setDetailState(previous => ({ id: selected, data: previous?.id === selected ? previous.data : null, error: getErrorMessage(e, "Não foi possível carregar a lista") }))
    })
    return () => { cancelled = true }
  }, [peladaId, selected, attempt])
  useEffect(() => {
    const visible = () => { if (document.visibilityState === "visible") refresh() }
    document.addEventListener("visibilitychange", visible)
    return () => document.removeEventListener("visibilitychange", visible)
  }, [refresh])
  async function mutate(signature: string, reason: string, action: (command: FinanceCommand) => Promise<FinanceResult>, expectedRevision?: number) {
    if (!overview || busy.current) throw new Error("Aguarde a operação em andamento")
    busy.current = true
    setPending(true)
    if (retryCommand.current?.signature !== signature) retryCommand.current = { signature, command: { revision: expectedRevision ?? overview.revision, requestKey: crypto.randomUUID(), reason: reason || null } }
    try {
      const result = await action(retryCommand.current.command)
      generation.current++
      detailGeneration.current++
      // the result carries the first page of open lists: keep the page on screen and reload it,
      // so a change never sends the user back to page 1 or to the other section
      setOverview(previous => previous ? { ...result.overview, lists: previous.lists, hasMore: previous.hasMore, total: previous.total } : result.overview)
      setError(null)
      if (result.detail) {
        const id = result.detail.collection.id
        setDetailState({ id, data: result.detail, error: null })
        // a new collection (create, duplicate) opens at its own URL
        if (id !== selected) select(id)
      }
      retryCommand.current = null
      refresh()
      return result
    } catch (e) {
      if (getErrorStatus(e) === 409) { retryCommand.current = null; refresh() }
      throw e
    } finally { busy.current = false; setPending(false) }
  }
  const setScope = (next: FinanceListScope) => { setScopeState(next); setPage(0) }
  const locked = pending || !writable
  return { overview, detail, selected, error, detailError, pending, writable, locked, scope, setScope, page, setPage, refresh, select, mutate }
}
export type FinanceController = ReturnType<typeof useFinance>
