import { useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import { InvitationDetails } from "@/components/invitations/InvitationDetails"
import { InvitationPreview } from "@/components/invitations/InvitationPreview"
import { useInvitation } from "@/components/invitations/hooks/useInvitation"
import { useInvitationResponse } from "@/components/invitations/hooks/useInvitationResponse"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/hooks/useAuth"
import { getErrorMessage, getErrorStatus } from "@/lib/errors"
import { invitationStatusLabel, invitationStatusStyle, type InvitationStatus } from "@/types/invitation"
import { formatInvitationDate, relativeToNow } from "@/utils/invitations"

// What the page says under the pelada for each state of the invitation
const statusNote: Record<InvitationStatus, string> = {
  PENDING: "Você só passa a fazer parte da pelada depois de aceitar.",
  ACCEPTED: "Você aceitou este convite.",
  DECLINED: "Você recusou este convite. Se mudar de ideia, peça um novo a um admin.",
  CANCELED: "Um admin cancelou este convite.",
  EXPIRED: "Este convite expirou. Peça um novo a um admin da pelada.",
}

export default function InvitationPage() {
  const { id = "" } = useParams()
  const { invitation, status, now, loading, error, reload } = useInvitation(id)
  const { pending, respond } = useInvitationResponse()
  const [actionError, setActionError] = useState<string | null>(null)
  const { logout } = useAuth()
  const navigate = useNavigate()

  async function answer(accept: boolean) {
    if (!invitation) return
    setActionError(null)
    const message = await respond(invitation.id, accept)
    if (message) setActionError(message)
    if (message || !accept) reload()
  }

  function changeAccount() {
    logout()
    navigate(`/auth?returnTo=${encodeURIComponent(`/invitations/${id}`)}`)
  }

  return (
    <div className="page-enter mx-auto flex w-full max-w-xl flex-col gap-4 px-4 py-6 md:py-12">
      {loading ? (
        <Skeleton className="h-96 rounded-hero" aria-label="Carregando convite" />
      ) : error || !invitation || !status ? (
        <Alert variant="destructive">
          <AlertTitle>{getErrorStatus(error) === 403 ? "Este convite é de outra conta" : "Convite indisponível"}</AlertTitle>
          <AlertDescription className="flex flex-col items-start gap-3">
            {getErrorMessage(error, "Não foi possível carregar o convite. Confira sua conexão.")}
            {getErrorStatus(error) === 403 ? (
              <Button variant="outline" size="sm" onClick={changeAccount}>Entrar com outra conta</Button>
            ) : (
              <Button variant="outline" size="sm" onClick={reload}>Tentar novamente</Button>
            )}
          </AlertDescription>
        </Alert>
      ) : (
        <Card className="flex flex-col gap-6 rounded-hero p-5 md:p-8">
          <div className="flex flex-col gap-3">
            <Badge variant="status" className={`self-start ${invitationStatusStyle[status]}`}>
              {invitationStatusLabel[status]}
            </Badge>
            <h1 className="font-display text-display font-bold leading-none md:text-display-lg">
              {status === "PENDING" ? "Convite para você" : "Convite para a pelada"}
            </h1>
          </div>
          <InvitationPreview pelada={invitation.pelada} size="lg" />
          <InvitationDetails
            sender={invitation.sender}
            rows={[
              { label: "Enviado", value: formatInvitationDate(invitation.createdAt) },
              status === "PENDING"
                ? { label: "Expira", value: relativeToNow(invitation.expiresAt, now) }
                : { label: "Validade", value: formatInvitationDate(invitation.expiresAt) },
            ]}
          />
          <p role="status" className="text-body text-muted-foreground">
            {invitation.isMember ? "Você já faz parte desta pelada." : statusNote[status]}
          </p>
          {actionError && (
            <Alert variant="destructive">
              <AlertDescription>{actionError}</AlertDescription>
            </Alert>
          )}
          <div className="flex flex-col gap-2 md:flex-row">
            {invitation.isMember ? (
              <Button asChild variant="brand" size="lg">
                <Link to={`/pelada/${invitation.peladaId}`}>Abrir pelada</Link>
              </Button>
            ) : (
              status === "PENDING" && (
                <>
                  <Button variant="brand" size="lg" disabled={pending !== null} onClick={() => answer(true)}>
                    {pending?.accept ? "Entrando…" : "Aceitar convite"}
                  </Button>
                  <Button variant="outline" size="lg" disabled={pending !== null} onClick={() => answer(false)}>
                    {pending && !pending.accept ? "Recusando…" : "Recusar"}
                  </Button>
                </>
              )
            )}
            <Button asChild variant="ghost" size="lg" className="md:ml-auto">
              <Link to="/home">Voltar ao início</Link>
            </Button>
          </div>
        </Card>
      )}
    </div>
  )
}
