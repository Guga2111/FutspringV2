import { useEffect, useState } from "react"
import { Link, useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { acceptInvitation, declineInvitation } from "@/api/invitations"
import { InvitationPreview } from "@/components/invitations/InvitationPreview"
import { useInvitations } from "@/components/invitations/hooks/useInvitations"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { useRealtime } from "@/hooks/useRealtime"
import { getErrorMessage } from "@/lib/errors"
import type { NotificationPush } from "@/types/notification"

export function PendingInvitations() {
  const { connectionId, subscribe } = useRealtime()
  const inbox = useInvitations(undefined, true, connectionId)
  const { reload } = inbox
  const { reload: reloadPeladas } = useMyPeladasContext()
  const [pending, setPending] = useState<number | null>(null)
  const navigate = useNavigate()

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

  async function respond(id: number, accept: boolean) {
    setPending(id)
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
      reload()
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível responder ao convite"))
      reload()
    } finally { setPending(null) }
  }

  if (inbox.loading) return <Skeleton className="h-28 rounded-tile" aria-label="Carregando convites" />
  if (!inbox.error && inbox.items.length === 0) return null
  return (
    <section className="flex flex-col gap-3" aria-labelledby="pending-invitations">
      <h2 id="pending-invitations" className="text-lg font-semibold">Convites para você</h2>
      {inbox.error && <Alert variant="destructive"><AlertDescription>Não foi possível carregar os convites. <Button variant="link" onClick={reload}>Tentar novamente</Button></AlertDescription></Alert>}
      {inbox.items.map(invitation => (
        <Card key={invitation.id}>
          <CardHeader><CardTitle className="text-sm text-muted-foreground">Convite para participar</CardTitle></CardHeader>
          <CardContent><InvitationPreview pelada={invitation.pelada} /></CardContent>
          <CardFooter className="flex flex-wrap gap-2">
            <Button disabled={pending !== null} onClick={() => respond(invitation.id, true)}>{pending === invitation.id ? "Aguarde…" : "Aceitar convite"}</Button>
            <Button variant="outline" disabled={pending !== null} onClick={() => respond(invitation.id, false)}>Recusar</Button>
            <Button asChild variant="link"><Link to={`/invitations/${invitation.id}`}>Ver convite</Link></Button>
          </CardFooter>
        </Card>
      ))}
      {inbox.hasMore && <Button variant="outline" disabled={inbox.loadingMore} onClick={inbox.loadMore}>{inbox.loadingMore ? "Carregando…" : "Carregar mais convites"}</Button>}
    </section>
  )
}
