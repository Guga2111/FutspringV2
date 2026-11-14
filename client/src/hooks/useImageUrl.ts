import { useCallback, useEffect, useState, useSyncExternalStore } from "react"
import { useAuth } from "@/hooks/useAuth"
import { imageSnapshot, subscribeImage } from "@/lib/privateImages"

// Attach ref to the photo's visible frame (including its fallback), not a hidden parent/list row.
// No HTTP request starts until this element approaches the viewport. Bytes stay shared only while in use.
export function useImageUrl(filename: string | null | undefined) {
  const { user } = useAuth()
  const userId = user?.id
  const [element, ref] = useState<HTMLElement | null>(null)
  const [visible, setVisible] = useState(() => typeof IntersectionObserver === "undefined")
  useEffect(() => {
    if (!element || visible) return
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        setVisible(true)
        observer.disconnect()
      }
    }, { rootMargin: "200px" })
    observer.observe(element)
    return () => observer.disconnect()
  }, [element, visible])

  const subscribe = useCallback((notify: () => void) => {
    if (!visible || !filename || !userId) return () => {}
    return subscribeImage(userId, filename, notify)
  }, [visible, filename, userId])
  const snapshot = useCallback(() => (
    visible && filename && userId ? imageSnapshot(userId, filename) : undefined
  ), [visible, filename, userId])
  const url = useSyncExternalStore(subscribe, snapshot, () => undefined)
  return { url, ref }
}
