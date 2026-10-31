import { useCallback, useEffect, useRef, useState } from "react"
import { getMyInvitations, getPeladaInvitations, INVITATIONS_PAGE_SIZE } from "@/api/invitations"
import type { Invitation } from "@/types/invitation"

// Paginated invitation inbox/history; results are keyed so route changes cannot display another pelada's rows.
export function useInvitations(peladaId?: number, pending = true, refreshKey = 0) {
  const key = `${peladaId ?? "mine"}:${pending}`
  const [version, setVersion] = useState(0)
  const [state, setState] = useState<{ key: string; version: number; items: Invitation[]; page: number; more: boolean; error: boolean } | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const requestId = useRef(0)
  const request = useCallback((page: number) => peladaId === undefined ? getMyInvitations(page) : getPeladaInvitations(peladaId, pending, page), [peladaId, pending])
  const reload = useCallback(() => setVersion(v => v + 1), [])

  useEffect(() => {
    let active = true
    const id = ++requestId.current
    request(0).then(items => {
      if (active && requestId.current === id) setState({ key, version, items, page: 0, more: items.length === INVITATIONS_PAGE_SIZE, error: false })
    }).catch(() => {
      if (active && requestId.current === id) setState({ key, version, items: [], page: 0, more: false, error: true })
    })
    return () => { active = false }
  }, [request, key, version, refreshKey])

  async function loadMore() {
    if (!state || state.key !== key || state.version !== version || !state.more || loadingMore) return
    const id = requestId.current
    setLoadingMore(true)
    try {
      const items = await request(state.page + 1)
      if (requestId.current !== id) return
      setState(previous => previous && ({ ...previous, items: [...previous.items, ...items.filter(i => !previous.items.some(old => old.id === i.id))], page: previous.page + 1, more: items.length === INVITATIONS_PAGE_SIZE, error: false }))
    } catch {
      if (requestId.current === id) setState(previous => previous && ({ ...previous, error: true }))
    } finally { setLoadingMore(false) }
  }
  const current = state?.key === key && state.version === version ? state : null
  return { items: current?.items ?? [], loading: !current, error: current?.error ?? false, hasMore: current?.more ?? false, loadingMore, reload, loadMore }
}
