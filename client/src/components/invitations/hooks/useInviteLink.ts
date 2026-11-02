import { useCallback, useEffect, useState } from "react"
import { toast } from "sonner"
import { getInviteLink, inviteLinkUrl, replaceInviteLink, revokeInviteLink } from "@/api/invitations"
import { useNowUntil } from "@/hooks/useNowUntil"
import { getErrorMessage } from "@/lib/errors"
import type { InviteLink } from "@/types/invitation"

type LinkAction = "create" | "replace" | "revoke"

const successMessage: Record<LinkAction, string> = {
  create: "Link criado",
  replace: "Novo link gerado",
  revoke: "Link revogado",
}

// The pelada's shareable invite link. Results are keyed by pelada id, so another pelada's link is never shown;
// a link past its expiry is treated as absent from that instant (useNowUntil).
export function useInviteLink(peladaId: number) {
  const [result, setResult] = useState<{ id: number; link: InviteLink | null; error: boolean } | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [pending, setPending] = useState<LinkAction | null>(null)

  useEffect(() => {
    let active = true
    getInviteLink(peladaId)
      .then((link) => { if (active) setResult({ id: peladaId, link, error: false }) })
      .catch(() => { if (active) setResult({ id: peladaId, link: null, error: true }) })
    return () => { active = false }
  }, [peladaId, attempt])

  const current = result?.id === peladaId ? result : null
  const now = useNowUntil(current?.link?.expiresAt ?? null)
  const link = current?.link && Date.parse(current.link.expiresAt) > now.getTime() ? current.link : null
  const url = link ? inviteLinkUrl(link.token) : null

  const run = useCallback(async (action: LinkAction): Promise<boolean> => {
    setPending(action)
    try {
      const next = action === "revoke" ? null : await replaceInviteLink(peladaId)
      if (action === "revoke") await revokeInviteLink(peladaId)
      setResult({ id: peladaId, link: next, error: false })
      toast.success(successMessage[action])
      return true
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível atualizar o link"))
      return false
    } finally {
      setPending(null)
    }
  }, [peladaId])

  async function copy() {
    if (!url) return
    try {
      await navigator.clipboard.writeText(url)
      toast.success("Link copiado")
    } catch {
      toast.error("Não foi possível copiar. Selecione o link e copie manualmente.")
    }
  }

  // the phone's share sheet (WhatsApp, Telegram…); a closed sheet rejects with AbortError and is not an error
  const canShare = typeof navigator !== "undefined" && typeof navigator.share === "function"
  async function share(peladaName: string) {
    if (!url || !canShare) return
    try {
      await navigator.share({ title: peladaName, text: `Entre na pelada ${peladaName} no Futspring`, url })
    } catch (error) {
      if (!(error instanceof DOMException && error.name === "AbortError")) toast.error("Não foi possível compartilhar o link.")
    }
  }

  return {
    link,
    url,
    now,
    loading: !current,
    error: current?.error ?? false,
    retry: () => setAttempt((v) => v + 1),
    pending,
    run,
    copy,
    canShare,
    share,
  }
}
