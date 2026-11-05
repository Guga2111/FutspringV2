import { useContext } from "react"
import { MyInvitationsContext } from "@/context/my-invitations-context-value"

export function useMyInvitationsContext() {
  const ctx = useContext(MyInvitationsContext)
  if (!ctx) {
    throw new Error("useMyInvitationsContext must be used within AppLayout")
  }
  return ctx
}
