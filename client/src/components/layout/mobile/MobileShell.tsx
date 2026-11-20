import { useCallback, useMemo, useRef, useState, type ReactNode } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import {
  MobileShellContext,
  type MobileHeaderConfig,
  type MobileShellContextValue,
  type RefreshHandler,
} from "@/context/mobile-shell-context-value"
import { useAuth } from "@/hooks/useAuth"
import { useEdgeSwipeBack, useSwipeBackAvailable } from "@/hooks/useEdgeSwipeBack"
import { useIsMobile } from "@/hooks/useIsMobile"
import { BottomTabBar } from "@/components/layout/mobile/BottomTabBar"
import { CreateMenu } from "@/components/layout/mobile/CreateMenu"
import { MobileHeader } from "@/components/layout/mobile/MobileHeader"
import { PullToRefresh } from "@/components/layout/mobile/PullToRefresh"
import { isTabRoot, parentPath } from "@/utils/mobileNav"

// The app's shell below md, like the apps people already use (Jakob's law): header with ←, bottom tabs with the "+",
// swipe back from the edge (iPhone's installed app) and pull to refresh. From md only the page shows: the header and
// the tabs are hidden and the sidebar takes over.
export function MobileShell({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()
  const isMobile = useIsMobile()
  const userId = user?.id ?? null
  const [header, setHeaderState] = useState<MobileHeaderConfig>({})
  const [actionsSlot, setActionsSlot] = useState<HTMLDivElement | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const refreshers = useRef(new Set<RefreshHandler>())
  const pageRef = useRef<HTMLDivElement>(null)
  const veilRef = useRef<HTMLDivElement>(null)
  const contentRef = useRef<HTMLDivElement>(null)

  const setHeader = useCallback((config: MobileHeaderConfig | null) => setHeaderState(config ?? {}), [])
  const parent = header.parent ?? parentPath(location.pathname, userId) ?? "/home"
  // the first entry of this visit (a WhatsApp link, a push, a reload) has nothing to go back to: the parent replaces
  // it. React Router numbers its entries in history.state.idx, which a replace keeps (location.key doesn't)
  const goBack = useCallback(() => {
    const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0
    if (idx === 0) navigate(parent, { replace: true })
    else navigate(-1)
  }, [navigate, parent])

  const registerRefresh = useCallback((handler: RefreshHandler) => {
    refreshers.current.add(handler)
    return () => {
      refreshers.current.delete(handler)
    }
  }, [])
  const refreshHandlers = useCallback(() => [...refreshers.current], [])

  const swipeAvailable = useSwipeBackAvailable()
  useEdgeSwipeBack(pageRef, veilRef, {
    enabled: isMobile && swipeAvailable && !isTabRoot(location.pathname, userId),
    onBack: goBack,
  })

  const value = useMemo<MobileShellContextValue>(
    () => ({ header, setHeader, actionsSlot, goBack, registerRefresh }),
    [header, setHeader, actionsSlot, goBack, registerRefresh],
  )

  return (
    <MobileShellContext.Provider value={value}>
      {/* behind the page while it follows the finger back */}
      <div ref={veilRef} aria-hidden className="pointer-events-none fixed inset-0 bg-overlay/40 opacity-0 md:hidden" />
      {/* above the veil below md only: from md the page keeps the desktop's stacking (the sidebar over shadows) */}
      <div ref={pageRef} className="flex min-w-0 flex-1 flex-col max-md:relative max-md:z-[1] max-md:bg-background">
        <MobileHeader contentRef={contentRef} onActionsSlot={setActionsSlot} />
        <div ref={contentRef} className="flex min-w-0 flex-1 flex-col">
          <PullToRefresh handlers={refreshHandlers} enabled={isMobile}>
            {children}
          </PullToRefresh>
        </div>
      </div>
      <BottomTabBar onCreate={() => setCreateOpen(true)} />
      <CreateMenu open={createOpen} onOpenChange={setCreateOpen} />
    </MobileShellContext.Provider>
  )
}
