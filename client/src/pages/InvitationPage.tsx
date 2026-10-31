import { useEffect, useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import { toast } from "sonner"
import { acceptInvitation, declineInvitation, getInvitation } from "@/api/invitations"
import { InvitationPreview } from "@/components/invitations/InvitationPreview"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/hooks/useAuth"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { useNowUntil } from "@/hooks/useNowUntil"
import { getErrorMessage, getErrorStatus } from "@/lib/errors"
import { invitationStatusLabel, type Invitation } from "@/types/invitation"

export default function InvitationPage() {
  const { id = "" } = useParams()
  const [version, setVersion] = useState(0)
  const [result, setResult] = useState<{ id: string; invitation?: Invitation; error?: unknown } | null>(null)
  const [pending, setPending] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const { reload } = useMyPeladasContext()
  const { logout } = useAuth()
  const navigate = useNavigate()
  useEffect(() => {
    let active = true
    getInvitation(Number(id)).then(invitation => { if (active) setResult({ id, invitation }) })
      .catch(error => { if (active) setResult({ id, error }) })
    return () => { active = false }
  }, [id, version])
  const current = result?.id === id ? result : null
  const invitation = current?.invitation
  const now = useNowUntil(invitation?.expiresAt ?? null)
  const status = invitation?.status === "PENDING" && now.getTime() >= Date.parse(invitation.expiresAt) ? "EXPIRED" : invitation?.status
  async function respond(accept: boolean) {
    if (!invitation) return
    setPending(true)
    setActionError(null)
    try {
      if (accept) {
        const joined = await acceptInvitation(invitation.id)
        reload()
        toast.success("Você entrou na pelada")
        navigate(`/pelada/${joined.peladaId}`)
      } else {
        await declineInvitation(invitation.id)
        toast.success("Convite recusado")
        setVersion(v => v + 1)
      }
    } catch (error) {
      setActionError(getErrorMessage(error, "Não foi possível responder. Confira sua conexão e tente novamente."))
      setVersion(v => v + 1)
    } finally { setPending(false) }
  }
  function changeAccount() {
    logout()
    navigate(`/auth?returnTo=${encodeURIComponent(`/invitations/${id}`)}`)
  }
  return (
    <main className="mx-auto flex w-full max-w-xl flex-col gap-4 px-4 py-8">
      {!current ? <Skeleton className="h-64 rounded-tile" /> : current.error ? (
        <Alert variant="destructive"><AlertDescription className="flex flex-col gap-3">
          {getErrorMessage(current.error, "Não foi possível carregar o convite. Confira sua conexão.")}
          {getErrorStatus(current.error) === 403 ? <Button variant="outline" onClick={changeAccount}>Entrar com outra conta</Button> : <Button variant="outline" onClick={() => setVersion(v => v + 1)}>Tentar novamente</Button>}
        </AlertDescription></Alert>
      ) : invitation && (
        <Card>
          <CardHeader><h1 className="text-2xl font-semibold tracking-tight">Convite para participar</h1></CardHeader>
          <CardContent className="flex flex-col gap-5">
            <InvitationPreview pelada={invitation.pelada} />
            {status === "PENDING" ? <p className="text-sm text-muted-foreground">Você só passa a participar depois de aceitar o convite.</p> : <p role="status" className="text-sm">Convite {status ? invitationStatusLabel[status].toLowerCase() : "encerrado"}.{status === "EXPIRED" && " Peça um novo convite a um admin."}</p>}
            {actionError && <Alert variant="destructive"><AlertDescription>{actionError}</AlertDescription></Alert>}
          </CardContent>
          <CardFooter className="flex flex-wrap gap-2">
            {invitation.isMember ? <Button asChild><Link to={`/pelada/${invitation.peladaId}`}>Abrir pelada</Link></Button> : status === "PENDING" && <>
              <Button disabled={pending} onClick={() => respond(true)}>{pending ? "Aguarde…" : "Aceitar convite"}</Button>
              <Button variant="outline" disabled={pending} onClick={() => respond(false)}>Recusar</Button>
            </>}
            <Button asChild variant="ghost"><Link to="/home">Voltar ao início</Link></Button>
          </CardFooter>
        </Card>
      )}
    </main>
  )
}
