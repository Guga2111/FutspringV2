import { useContext, useEffect, useLayoutEffect, useRef } from "react"
import { MobileShellContext, type MobileHeaderConfig, type RefreshHandler } from "@/context/mobile-shell-context-value"

// null outside the private shell (public pages)
export function useMobileShell() {
  return useContext(MobileShellContext)
}

// The page's title, subtitle, parent and pelada for the mobile header; cleared when the page leaves
export function useMobileHeader({ title, subtitle, parent, peladaId }: MobileHeaderConfig) {
  const setHeader = useContext(MobileShellContext)?.setHeader
  useEffect(() => {
    if (!setHeader) return
    setHeader({ title, subtitle, parent, peladaId })
    return () => setHeader(null)
  }, [setHeader, title, subtitle, parent, peladaId])
}

// Registers the page's reads for pull to refresh while it is mounted. The handler must return the promise of the
// reads (the spinner waits for it). A page without a registration doesn't react to the gesture.
export function useRefresh(handler: RefreshHandler) {
  const register = useContext(MobileShellContext)?.registerRefresh
  const latest = useRef(handler)
  useLayoutEffect(() => {
    latest.current = handler
  })
  useEffect(() => register?.(() => latest.current()), [register])
}
