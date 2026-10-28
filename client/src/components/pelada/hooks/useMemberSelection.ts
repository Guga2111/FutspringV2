import { useCallback, useEffect, useMemo, useState } from "react"
import type { PeladaMember } from "@/types/pelada"

// Selection mode of the members grid. The selection survives search and filter changes; `selected` is derived from
// the members on screen, so removed members drop out of it on their own
export function useMemberSelection(members: PeladaMember[], visible: PeladaMember[]) {
  const [active, setActive] = useState(false)
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<number>>(() => new Set())

  const enter = useCallback(() => setActive(true), [])
  const exit = useCallback(() => {
    setActive(false)
    setSelectedIds(new Set())
  }, [])

  const toggle = useCallback((id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const selected = useMemo(() => members.filter((m) => selectedIds.has(m.id)), [members, selectedIds])
  const allVisibleSelected = visible.length > 0 && visible.every((m) => selectedIds.has(m.id))
  const outsideFilter = useMemo(() => {
    const visibleIds = new Set(visible.map((m) => m.id))
    return selected.filter((m) => !visibleIds.has(m.id)).length
  }, [selected, visible])

  // Marks every visible member, or unmarks them when all of them are already marked
  const toggleVisible = useCallback(() => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      const unmark = visible.length > 0 && visible.every((m) => prev.has(m.id))
      for (const m of visible) {
        if (unmark) next.delete(m.id)
        else next.add(m.id)
      }
      return next
    })
  }, [visible])

  // Esc leaves selection mode; an open dialog or menu handles its own Esc first
  useEffect(() => {
    if (!active) return
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !event.defaultPrevented) exit()
    }
    document.addEventListener("keydown", onKeyDown)
    return () => document.removeEventListener("keydown", onKeyDown)
  }, [active, exit])

  return { active, selectedIds, selected, outsideFilter, allVisibleSelected, enter, exit, toggle, toggleVisible }
}
