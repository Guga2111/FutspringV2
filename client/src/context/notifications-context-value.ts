import { createContext } from "react"
import type { NotificationResponse } from "@/types/notification"

export type NotificationFilter = "all" | "unread"

// The caller's notifications, loaded by AppLayout for the bell in the sidebar and the mobile top bar
export interface NotificationsContextValue {
  // null until the first count arrives
  unreadCount: number | null
  items: NotificationResponse[]
  filter: NotificationFilter
  // first page of the current filter: loading (no items yet) / error
  loading: boolean
  error: boolean
  hasMore: boolean
  loadingMore: boolean
  // the panel opened: loads the list (first open) or refreshes its first page, and the counter
  refresh: () => void
  setFilter: (filter: NotificationFilter) => void
  loadMore: () => void
  // optimistic; reverts with a toast on error
  markRead: (notification: NotificationResponse) => void
  markAllRead: () => void
  // the "Você perdeu a vaga" alert of a session was dismissed: its notification is read too
  markBumpRead: (dailyId: number) => void
}

export const NotificationsContext = createContext<NotificationsContextValue | null>(null)
