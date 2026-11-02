import { useCallback, useState } from "react"

export type SessionsView = "list" | "gallery"

const STORAGE_KEY = "sessions_view"

function readView(): SessionsView {
  try {
    return localStorage.getItem(STORAGE_KEY) === "gallery" ? "gallery" : "list"
  } catch {
    return "list"
  }
}

// List or gallery on the Sessões page, remembered per browser (a convenience: storage may be unavailable)
export function useSessionsView(): [SessionsView, (view: SessionsView) => void] {
  const [view, setView] = useState<SessionsView>(readView)
  const change = useCallback((next: SessionsView) => {
    setView(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // private window or blocked storage: the choice lasts until reload
    }
  }, [])
  return [view, change]
}
