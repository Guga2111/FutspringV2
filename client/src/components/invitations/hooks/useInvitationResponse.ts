import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { acceptInvitation, declineInvitation } from "@/api/invitations"
import { useMyInvitationsContext } from "@/hooks/useMyInvitationsContext"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { getErrorMessage } from "@/lib/errors"

// Accept / decline an individual invitation. Accepting reloads the sidebar's peladas and opens the pelada;
// both answers refresh the pending invitations (sidebar badge, Home); a failure returns the API message so the caller shows it where it fits (toast or inline alert).
export function useInvitationResponse() {
  const navigate = useNavigate()
  const { reload: reloadPeladas } = useMyPeladasContext()
  const { reload: reloadInvitations } = useMyInvitationsContext()
  const [pending, setPending] = useState<{ id: number; accept: boolean } | null>(null)

  async function respond(id: number, accept: boolean): Promise<string | null> {
    setPending({ id, accept })
    try {
      if (accept) {
        const joined = await acceptInvitation(id)
        reloadPeladas()
        toast.success("Você entrou na pelada")
        navigate(`/pelada/${joined.peladaId}`)
      } else {
        await declineInvitation(id)
        toast.success("Convite recusado")
      }
      reloadInvitations()
      return null
    } catch (error) {
      return getErrorMessage(error, "Não foi possível responder. Confira sua conexão e tente novamente.")
    } finally {
      setPending(null)
    }
  }

  return { pending, respond }
}
