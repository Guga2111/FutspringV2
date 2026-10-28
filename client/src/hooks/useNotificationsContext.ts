import { useContext } from "react"
import { NotificationsContext } from "@/context/notifications-context-value"

export function useNotificationsContext() {
  const ctx = useContext(NotificationsContext)
  if (!ctx) {
    throw new Error("useNotificationsContext must be used within AppLayout")
  }
  return ctx
}
