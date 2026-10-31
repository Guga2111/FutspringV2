import { useEffect, useState } from "react"
import { toast } from "sonner"
import { cancelInvitation, getInviteLink, inviteLinkUrl, replaceInviteLink, revokeInviteLink } from "@/api/invitations"
import { useInvitations } from "@/components/invitations/hooks/useInvitations"
import { ConfirmActionDialog } from "@/components/ConfirmActionDialog"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Field, FieldLabel } from "@/components/ui/field"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { getErrorMessage } from "@/lib/errors"
import { useNowUntil } from "@/hooks/useNowUntil"
import { invitationStatusLabel, type InviteLink, type Invitation } from "@/types/invitation"

const date = (iso: string) => new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })

function InvitationHistory({ peladaId, pending, version }: { peladaId: number; pending: boolean; version: number }) {
  const list = useInvitations(peladaId, pending, version)
  const [canceling, setCanceling] = useState<Invitation | null>(null)
  const [busy, setBusy] = useState(false)
  async function cancel() {
    if (!canceling) return
    setBusy(true)
    try {
      await cancelInvitation(peladaId, canceling.id)
      setCanceling(null)
      toast.success("Convite cancelado")
      list.reload()
    } catch (error) { toast.error(getErrorMessage(error, "Não foi possível cancelar o convite")) }
    finally { setBusy(false) }
  }
  return (
    <div className="flex flex-col gap-3">
      {list.loading ? <Skeleton className="h-32" /> : <>
        {list.error && <Alert variant="destructive"><AlertDescription>Não foi possível carregar os convites. <Button variant="link" onClick={list.reload}>Tentar novamente</Button></AlertDescription></Alert>}
        {!list.error && list.items.length === 0 && <p className="py-4 text-sm text-muted-foreground">{pending ? "Nenhum convite pendente." : "Nenhum convite encerrado."}</p>}
        {list.items.map(invitation => (
          <div key={invitation.id} className="flex flex-wrap items-start justify-between gap-3 rounded-tile bg-muted/50 p-4">
            <div className="flex min-w-0 flex-col gap-1">
              <p className="break-words font-medium">{invitation.recipient.username}</p>
              <p className="break-words text-sm text-muted-foreground">Convidado por {invitation.sender.username} em {date(invitation.createdAt)}</p>
              <p className="text-sm text-muted-foreground">{invitation.respondedAt ? `Respondido em ${date(invitation.respondedAt)}` : invitation.canceledAt ? `Cancelado em ${date(invitation.canceledAt)}` : `${invitation.status === "EXPIRED" ? "Expirou" : "Expira"} em ${date(invitation.expiresAt)}`}</p>
            </div>
            <div className="flex items-center gap-2">
              <Badge variant="secondary">{invitationStatusLabel[invitation.status]}</Badge>
              {invitation.status === "PENDING" && <Button size="sm" variant="outline" onClick={() => setCanceling(invitation)}>Cancelar</Button>}
            </div>
          </div>
        ))}
        {list.hasMore && <Button variant="outline" disabled={list.loadingMore} onClick={list.loadMore}>{list.loadingMore ? "Carregando…" : "Carregar mais"}</Button>}
      </>}
      {canceling && <ConfirmActionDialog title="Cancelar convite?" description={`O convite de ${canceling.recipient.username} deixará de permitir a entrada na pelada.`} confirmLabel="Cancelar convite" pendingLabel="Cancelando…" cancelLabel="Manter convite" loading={busy} onConfirm={cancel} onClose={() => setCanceling(null)} />}
    </div>
  )
}

export function PeladaInvitations({ peladaId, version, onInvite }: { peladaId: number; version: number; onInvite: () => void }) {
  const [result, setResult] = useState<{ id: number; link: InviteLink | null; error: boolean } | null>(null)
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState<"replace" | "revoke" | null>(null)
  const [historyVersion, setHistoryVersion] = useState(0)
  useEffect(() => {
    let active = true
    getInviteLink(peladaId).then(link => { if (active) setResult({ id: peladaId, link, error: false }) })
      .catch(() => { if (active) setResult({ id: peladaId, link: null, error: true }) })
    return () => { active = false }
  }, [peladaId, reload])
  const current = result?.id === peladaId ? result : null
  const now = useNowUntil(current?.link?.expiresAt ?? null)
  const link = current?.link && Date.parse(current.link.expiresAt) > now.getTime() ? current.link : null
  async function mutate(action: "replace" | "revoke") {
    setBusy(true)
    try {
      let next: InviteLink | null = null
      if (action === "replace") next = await replaceInviteLink(peladaId)
      else await revokeInviteLink(peladaId)
      setResult({ id: peladaId, link: next, error: false })
      setConfirm(null)
      toast.success(action === "replace" ? "Link criado" : "Link revogado")
    } catch (error) { toast.error(getErrorMessage(error, "Não foi possível atualizar o link")) }
    finally { setBusy(false) }
  }
  async function copy() {
    if (!link) return
    try { await navigator.clipboard.writeText(inviteLinkUrl(link.token)); toast.success("Link copiado") }
    catch { toast.error("Não foi possível copiar. Selecione o link e copie manualmente.") }
  }
  return (
    <div className="flex min-w-0 flex-col gap-5">
      <Card>
        <CardHeader><CardTitle>Link de convite</CardTitle><CardDescription>Quem tiver o link poderá confirmar a entrada. Válido por 7 dias.</CardDescription></CardHeader>
        <CardContent className="flex flex-col gap-3">
          {!current ? <Skeleton className="h-20" /> : current.error ? <Alert variant="destructive"><AlertDescription>Não foi possível carregar o link. <Button variant="link" onClick={() => setReload(v => v + 1)}>Tentar novamente</Button></AlertDescription></Alert> : link ? <>
            <Field><FieldLabel htmlFor="pelada-invite-link">Link compartilhável</FieldLabel><Input id="pelada-invite-link" readOnly value={inviteLinkUrl(link.token)} onFocus={e => e.target.select()} /></Field>
            <p className="text-sm text-muted-foreground">Criado por {link.creator.username} em {date(link.createdAt)}. Expira em {date(link.expiresAt)}.</p>
          </> : <p className="text-sm text-muted-foreground">Nenhum link ativo.</p>}
        </CardContent>
        <CardFooter className="flex flex-wrap gap-2">
          {link ? <>
            <Button disabled={busy} onClick={copy}>Copiar link</Button>
            <Button variant="outline" disabled={busy} onClick={() => setConfirm("replace")}>Substituir link</Button>
            <Button variant="outline" disabled={busy} onClick={() => setConfirm("revoke")}>Revogar link</Button>
          </> : <Button disabled={busy || !current || current.error} onClick={() => mutate("replace")}>{busy ? "Criando…" : "Criar link"}</Button>}
        </CardFooter>
      </Card>
      <Card>
        <CardHeader><CardTitle>Convites individuais</CardTitle><CardDescription>Convide uma conta cadastrada. O jogador decide se quer participar.</CardDescription></CardHeader>
        <CardContent>
          <Tabs defaultValue="pending" onValueChange={() => setHistoryVersion(v => v + 1)}>
            <TabsList className="mb-4 w-full" aria-label="Situação dos convites"><TabsTrigger className="flex-1" value="pending">Pendentes</TabsTrigger><TabsTrigger className="flex-1" value="history">Histórico</TabsTrigger></TabsList>
            <TabsContent value="pending"><InvitationHistory peladaId={peladaId} pending version={version + historyVersion} /></TabsContent>
            <TabsContent value="history"><InvitationHistory peladaId={peladaId} pending={false} version={version + historyVersion} /></TabsContent>
          </Tabs>
        </CardContent>
        <CardFooter><Button onClick={onInvite}>Convidar jogador</Button></CardFooter>
      </Card>
      {confirm && <ConfirmActionDialog title={confirm === "replace" ? "Substituir o link?" : "Revogar o link?"} description="O link atual deixará de permitir a entrada na pelada." confirmLabel={confirm === "replace" ? "Substituir link" : "Revogar link"} pendingLabel="Aguarde…" loading={busy} onConfirm={() => mutate(confirm)} onClose={() => setConfirm(null)} />}
    </div>
  )
}
