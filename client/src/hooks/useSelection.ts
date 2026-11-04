import { useCallback, useEffect, useMemo, useState } from "react"

const everyItem = () => true

// Selection mode of a list (members grid, finance charges). The selection survives search and filter changes;
// `selected` is derived from the current items, so items that leave the list drop out of it on their own.
// `selectable` limits what "select visible" marks (pass a stable function, e.g. a module-level one)
export function useSelection<T extends { id: number }>(
  items: T[],
  visible: T[],
  selectable: (item: T) => boolean = everyItem,
) {
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

  // Marks these ids, keeping the ones already marked (e.g. "Selecionar mensalistas")
  const select = useCallback((ids: number[]) => {
    setSelectedIds((prev) => new Set([...prev, ...ids]))
  }, [])

  const selected = useMemo(() => items.filter((item) => selectedIds.has(item.id)), [items, selectedIds])
  const visibleSelectable = useMemo(() => visible.filter(selectable), [visible, selectable])
  const allVisibleSelected = visibleSelectable.length > 0 && visibleSelectable.every((item) => selectedIds.has(item.id))
  const outsideFilter = useMemo(() => {
    const visibleIds = new Set(visible.map((item) => item.id))
    return selected.filter((item) => !visibleIds.has(item.id)).length
  }, [selected, visible])

  // Marks every selectable visible item, or unmarks them when all of them are already marked
  const toggleVisible = useCallback(() => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      const unmark = visibleSelectable.length > 0 && visibleSelectable.every((item) => prev.has(item.id))
      for (const item of visibleSelectable) {
        if (unmark) next.delete(item.id)
        else next.add(item.id)
      }
      return next
    })
  }, [visibleSelectable])

  // Esc leaves selection mode; an open dialog or menu handles its own Esc first
  useEffect(() => {
    if (!active) return
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !event.defaultPrevented) exit()
    }
    document.addEventListener("keydown", onKeyDown)
    return () => document.removeEventListener("keydown", onKeyDown)
  }, [active, exit])

  return { active, selectedIds, selected, outsideFilter, allVisibleSelected, enter, exit, toggle, toggleVisible, select }
}
