import { useEffect } from "react"
import { Link } from "react-router-dom"
import { toast } from "sonner"
import { InvitationPreview } from "@/components/invitations/InvitationPreview"
import { useInvitationResponse } from "@/components/invitations/hooks/useInvitationResponse"
import { useInvitations } from "@/components/invitations/hooks/useInvitations"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useNowUntil } from "@/hooks/useNowUntil"
import { useRealtime } from "@/hooks/useRealtime"
import type { NotificationPush } from "@/types/notification"
import { relativeToNow } from "@/utils/invitations"

export function PendingInvitations() {
  const { connectionId, subscribe } = useRealtime()
  const inbox = useInvitations(undefined, true, connectionId)
  const { reload } = inbox
  const { pending, respond: send } = useInvitationResponse()
  const now = useNowUntil(null)

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
    const error = await send(id, accept)
    if (error) toast.error(error)
    reload()
  }

  if (inbox.loading) return <Skeleton className="h-40 rounded-tile" aria-label="Carregando convites" />
  if (!inbox.error && inbox.items.length === 0) return null
  return (
    <section className="flex flex-col gap-3.5" aria-labelledby="pending-invitations">
      <div className="flex items-baseline gap-2.5">
        <h2 id="pending-invitations" className="text-title font-semibold">Convites para você</h2>
        <span className="text-body-sm text-subtle-foreground">{inbox.items.length}{inbox.hasMore ? "+" : ""}</span>
      </div>
      {inbox.error && (
        <Alert variant="destructive">
          <AlertDescription>
            Não foi possível carregar os convites.{" "}
            <Button variant="link" className="h-auto p-0" onClick={reload}>Tentar novamente</Button>
          </AlertDescription>
        </Alert>
      )}
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        {inbox.items.map((invitation) => {
          const busy = pending !== null
          const accepting = pending?.id === invitation.id && pending.accept
          return (
            <Card key={invitation.id} className="flex flex-col gap-4 p-4">
              <InvitationPreview pelada={invitation.pelada} />
              <p className="flex min-w-0 items-center gap-2 text-body-sm text-muted-foreground">
                <PlayerAvatar userId={invitation.sender.id} username={invitation.sender.username} image={invitation.sender.image} className="size-6 text-overline" />
                <span className="truncate">
                  <span className="font-medium text-foreground">{invitation.sender.username}</span> te convidou · expira {relativeToNow(invitation.expiresAt, now)}
                </span>
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <Button variant="brand" className="flex-1 md:flex-none" disabled={busy} onClick={() => respond(invitation.id, true)}>
                  {accepting ? "Entrando…" : "Aceitar convite"}
                </Button>
                <Button variant="outline" className="flex-1 md:flex-none" disabled={busy} onClick={() => respond(invitation.id, false)}>
                  Recusar
                </Button>
                <Button asChild variant="ghost" className="w-full md:ml-auto md:w-auto">
                  <Link to={`/invitations/${invitation.id}`}>Ver detalhes</Link>
                </Button>
              </div>
            </Card>
          )
        })}
      </div>
      {inbox.hasMore && (
        <Button variant="outline" className="self-center" disabled={inbox.loadingMore} onClick={inbox.loadMore}>
          {inbox.loadingMore ? "Carregando…" : "Carregar mais convites"}
        </Button>
      )}
    </section>
  )
}
