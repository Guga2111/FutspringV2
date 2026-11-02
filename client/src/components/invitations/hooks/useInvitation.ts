import { useCallback, useEffect, useState } from "react"
import { getInvitation } from "@/api/invitations"
import { useNowUntil } from "@/hooks/useNowUntil"
import type { Invitation, InvitationStatus } from "@/types/invitation"

// One invitation of the caller (/invitations/:id), keyed by id. A pending invitation past its expiry
// reads as EXPIRED from that instant, like the backend's effective expiry.
export function useInvitation(id: string) {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<{ id: string; invitation?: Invitation; error?: unknown } | null>(null)

  useEffect(() => {
    let active = true
    getInvitation(Number(id))
      .then((invitation) => { if (active) setResult({ id, invitation }) })
      .catch((error) => { if (active) setResult({ id, error }) })
    return () => { active = false }
  }, [id, attempt])

  const current = result?.id === id ? result : null
  const invitation = current?.invitation ?? null
  const now = useNowUntil(invitation?.expiresAt ?? null)
  const status: InvitationStatus | null = invitation
    ? invitation.status === "PENDING" && now.getTime() >= Date.parse(invitation.expiresAt) ? "EXPIRED" : invitation.status
    : null

  return {
    invitation,
    status,
    now,
    loading: !current,
    error: current?.error ?? null,
    reload: useCallback(() => setAttempt((v) => v + 1), []),
  }
}
