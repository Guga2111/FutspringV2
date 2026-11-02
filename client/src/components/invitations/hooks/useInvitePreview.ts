import { useCallback, useEffect, useState } from "react"
import { getInvitePreview } from "@/api/invitations"
import { getMyPeladas } from "@/api/peladas"
import type { PeladaPreview } from "@/types/invitation"

// The public preview of a share link (/join/:token), keyed by token. With a session it also checks whether
// the caller already belongs to the pelada (the page outside the shell has no MyPeladasContext); joining
// is checked again on the server.
export function useInvitePreview(token: string, session: string | null) {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<{ token: string; preview?: PeladaPreview; error?: unknown } | null>(null)
  const [member, setMember] = useState<{ token: string; session: string; id: number } | null>(null)

  useEffect(() => {
    let active = true
    getInvitePreview(token)
      .then((preview) => { if (active) setResult({ token, preview }) })
      .catch((error) => { if (active) setResult({ token, error }) })
    return () => { active = false }
  }, [token, attempt])

  const current = result?.token === token ? result : null
  const preview = current?.preview ?? null

  useEffect(() => {
    if (!session || !preview) return
    let active = true
    getMyPeladas()
      .then((peladas) => { if (active && peladas.some((p) => p.id === preview.id)) setMember({ token, session, id: preview.id }) })
      .catch(() => { /* joining still checks membership on the server */ })
    return () => { active = false }
  }, [session, preview, token])

  return {
    preview,
    loading: !current,
    error: current?.error ?? null,
    memberPeladaId: member?.token === token && member.session === session ? member.id : null,
    retry: useCallback(() => setAttempt((v) => v + 1), []),
  }
}
