import { useEffect } from "react"
import { useInvitations } from "@/components/invitations/hooks/useInvitations"
import { useRealtime } from "@/hooks/useRealtime"
import type { MyInvitationsContextValue } from "@/context/my-invitations-context-value"
import type { NotificationPush } from "@/types/notification"

// Called once by AppLayout (read it with useMyInvitationsContext): the caller's pending invitations,
// refetched on every (re)connect, on a PELADA_INVITATION push and when the tab becomes visible again.
export function useMyInvitations(): MyInvitationsContextValue {
  const { connectionId, subscribe } = useRealtime()
  const inbox = useInvitations(undefined, true, connectionId)
  const { reload } = inbox

  useEffect(() => subscribe("/user/queue/notifications", body => {
    try {
      const push = JSON.parse(body) as NotificationPush
      if (push.notification.type === "PELADA_INVITATION") reload()
    } catch { /* An invalid frame must not interrupt the inbox. */ }
  }), [subscribe, reload])
  useEffect(() => {
    const visible = () => { if (document.visibilityState === "visible") reload() }
    document.addEventListener("visibilitychange", visible)
    return () => document.removeEventListener("visibilitychange", visible)
  }, [reload])

  return inbox
}
