import { createContext } from "react"
import type { Invitation } from "@/types/invitation"

// The caller's pending individual invitations, loaded once by AppLayout for the sidebar badge and the Home inbox
export interface MyInvitationsContextValue {
  items: Invitation[]
  loading: boolean
  error: boolean
  // more pages exist: counts show "20+"
  hasMore: boolean
  loadingMore: boolean
  reload: () => void
  loadMore: () => Promise<void>
}

export const MyInvitationsContext = createContext<MyInvitationsContextValue | null>(null)
