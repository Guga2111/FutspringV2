import { useCallback, useEffect, useRef, useState } from "react"
import * as api from "@/api/finance"
import type { FinanceCommand } from "@/schemas/finance"
import type { FinanceOverview, FinanceDetail, FinanceResult } from "@/types/finance"
import { getErrorMessage, getErrorStatus } from "@/lib/errors"

export function useFinance(peladaId: number) {
  const [attempt, setAttempt] = useState(0)
  const [page, setPage] = useState(0)
  const [overview, setOverview] = useState<FinanceOverview | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<number | null>(null)
  const [detail, setDetail] = useState<FinanceDetail | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const generation = useRef(0)
  const detailGeneration = useRef(0)
  const retryCommand = useRef<{ signature: string; command: FinanceCommand } | null>(null)
  const refresh = useCallback(() => setAttempt(v => v + 1), [])
  useEffect(() => {
    let cancelled = false
    const request = ++generation.current
    api.getFinance(peladaId, page).then(data => {
      if (!cancelled && generation.current === request) { setOverview(data); setError(null) }
    }).catch(e => {
      if (!cancelled && generation.current === request) setError(getErrorMessage(e, "Não foi possível carregar o financeiro"))
    })
    return () => { cancelled = true }
  }, [peladaId, page, attempt])
  useEffect(() => {
    if (selected === null) return
    let cancelled = false
    const request = ++detailGeneration.current
    api.getFinanceList(peladaId, selected).then(data => {
      if (!cancelled && detailGeneration.current === request) { setDetail(data); setDetailError(null) }
    }).catch(e => {
      if (!cancelled && detailGeneration.current === request) setDetailError(getErrorMessage(e, "Não foi possível carregar a lista"))
    })
    return () => { cancelled = true }
  }, [peladaId, selected, attempt])
  useEffect(() => {
    const visible = () => { if (document.visibilityState === "visible") refresh() }
    document.addEventListener("visibilitychange", visible)
    return () => document.removeEventListener("visibilitychange", visible)
  }, [refresh])
  const select = (id: number | null) => { setSelected(id); setDetail(null); setDetailError(null) }
  async function mutate(signature: string, reason: string, action: (command: FinanceCommand) => Promise<FinanceResult>, expectedRevision?: number) {
    if (!overview || busy.current) throw new Error("Aguarde a operação em andamento")
    busy.current = true
    setPending(true)
    if (retryCommand.current?.signature !== signature) retryCommand.current = { signature, command: { revision: expectedRevision ?? overview.revision, requestKey: crypto.randomUUID(), reason: reason || null } }
    try {
      const result = await action(retryCommand.current.command)
      generation.current++
      detailGeneration.current++
      setOverview(result.overview)
      setError(null)
      if (result.detail) { setDetail(result.detail); setSelected(result.detail.collection.id); setDetailError(null) }
      retryCommand.current = null
      setPage(0)
      return result
    } catch (e) {
      if (getErrorStatus(e) === 409) { retryCommand.current = null; refresh() }
      throw e
    } finally { busy.current = false; setPending(false) }
  }
  return { overview, detail, selected, error, detailError, pending, page, setPage, refresh, select, mutate }
}
export type FinanceController = ReturnType<typeof useFinance>
