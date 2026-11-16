import { createContext } from "react"

// What a page tells the mobile header (below md). Everything is optional: without a title the header shows the
// page's own h1 once it scrolls away (iOS "large title"), and without a parent ← falls back to parentPath()
export interface MobileHeaderConfig {
  // always shown (the pelada's name over its areas)
  title?: string
  // a second line under the title (the chat's "12 membros")
  subtitle?: string
  // where ← goes when there's no history (a session's pelada, read from its detail)
  parent?: string
  // the pelada on screen when the URL doesn't say it (a session): the "+" preselects it
  peladaId?: number
}

export type RefreshHandler = () => Promise<unknown>

export interface MobileShellContextValue {
  header: MobileHeaderConfig
  setHeader: (config: MobileHeaderConfig | null) => void
  // the header's right side, where MobileHeaderActions portals each page's buttons
  actionsSlot: HTMLElement | null
  // ← and the swipe back: history back, or the parent when the page was the first one opened
  goBack: () => void
  // pull to refresh: the reads of the page on screen; returns the unregister
  registerRefresh: (handler: RefreshHandler) => () => void
}

export const MobileShellContext = createContext<MobileShellContextValue | null>(null)
