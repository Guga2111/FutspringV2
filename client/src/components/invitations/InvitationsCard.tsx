import { useState, type ReactNode } from "react"
import { toast } from "sonner"
import { HugeiconsIcon } from "@hugeicons/react"
import { UserAdd01Icon } from "@hugeicons/core-free-icons"
import { cancelInvitation } from "@/api/invitations"
import { useInvitations } from "@/components/invitations/hooks/useInvitations"
import { InvitationList } from "@/components/invitations/InvitationList"
import { ConfirmActionDialog } from "@/components/ConfirmActionDialog"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useNowUntil } from "@/hooks/useNowUntil"
import { getErrorMessage } from "@/lib/errors"
import type { Invitation } from "@/types/invitation"

type ListState = ReturnType<typeof useInvitations>

function ListBody({ list, empty, onCancel, disabled }: { list: ListState; empty: ReactNode; onCancel?: (i: Invitation) => void; disabled?: boolean }) {
  // the pending rows say "expira em 5 dias"
  const now = useNowUntil(null)
  if (list.loading) {
    return (
      <div className="flex flex-col gap-3 p-4 md:p-6" aria-hidden>
        {[0, 1, 2].map((i) => <Skeleton key={i} className="h-12 rounded-tile" />)}
      </div>
    )
  }
  if (list.error && list.items.length === 0) {
    return (
      <div className="p-4 md:p-6">
        <Alert variant="destructive">
          <AlertDescription>
            Não foi possível carregar os convites.{" "}
            <Button variant="link" className="h-auto p-0" onClick={list.reload}>Tentar novamente</Button>
          </AlertDescription>
        </Alert>
      </div>
    )
  }
  if (list.items.length === 0) return <div className="flex flex-col items-start gap-3 p-4 md:p-6">{empty}</div>
  return (
    <>
      <InvitationList items={list.items} now={now} onCancel={onCancel} disabled={disabled} />
      {list.hasMore && (
        <div className="flex justify-center border-t p-4">
          <Button variant="outline" size="sm" disabled={list.loadingMore} onClick={list.loadMore}>
            {list.loadingMore ? "Carregando…" : "Carregar mais"}
          </Button>
        </div>
      )}
    </>
  )
}

// Mounted only while its tab is open (Radix unmounts inactive content), so opening it refetches
function HistoryTab({ peladaId, version }: { peladaId: number; version: number }) {
  const list = useInvitations(peladaId, false, version)
  return <ListBody list={list} empty={<p className="text-body text-muted-foreground">Nenhum convite encerrado ainda.</p>} />
}

export function InvitationsCard({ peladaId, version, onInvite }: { peladaId: number; version: number; onInvite: () => void }) {
  const pending = useInvitations(peladaId, true, version)
  const [canceling, setCanceling] = useState<Invitation | null>(null)
  const [busy, setBusy] = useState(false)

  async function cancel() {
    if (!canceling) return
    setBusy(true)
    try {
      await cancelInvitation(peladaId, canceling.id)
      setCanceling(null)
      toast.success("Convite cancelado")
      pending.reload()
    } catch (error) {
      toast.error(getErrorMessage(error, "Não foi possível cancelar o convite"))
    } finally {
      setBusy(false)
    }
  }

  const count = pending.loading || pending.error || pending.items.length === 0 ? null : `${pending.items.length}${pending.hasMore ? "+" : ""}`

  return (
    <Card className="overflow-hidden">
      <CardHeader className="flex flex-col gap-3 space-y-0 p-4 md:flex-row md:items-start md:justify-between md:p-6">
        <div className="flex min-w-0 flex-col gap-1">
          <CardTitle className="text-title font-semibold">Convites individuais</CardTitle>
          <CardDescription>Convide uma conta cadastrada. O jogador decide se quer entrar.</CardDescription>
        </div>
        <Button variant="brand" onClick={onInvite} className="w-full shrink-0 md:w-auto">
          <HugeiconsIcon icon={UserAdd01Icon} className="size-[15px]" strokeWidth={2.2} />
          Convidar jogador
        </Button>
      </CardHeader>

      <Tabs defaultValue="pending">
        <TabsList variant="underline" className="w-full px-4 md:px-6" aria-label="Situação dos convites">
          <TabsTrigger variant="underline" value="pending" className="gap-2">
            Pendentes
            {count && (
              <span className="rounded-full bg-secondary px-2 py-0.5 text-caption font-semibold tabular-nums text-secondary-foreground">
                {count}
              </span>
            )}
          </TabsTrigger>
          <TabsTrigger variant="underline" value="history">Histórico</TabsTrigger>
        </TabsList>
        <TabsContent value="pending" className="mt-0">
          <ListBody
            list={pending}
            onCancel={setCanceling}
            disabled={busy}
            empty={<p className="text-body text-muted-foreground">Nenhum convite pendente.</p>}
          />
        </TabsContent>
        <TabsContent value="history" className="mt-0">
          <HistoryTab peladaId={peladaId} version={version} />
        </TabsContent>
      </Tabs>

      {canceling && (
        <ConfirmActionDialog
          title="Cancelar convite?"
          description={`O convite de ${canceling.recipient.username} deixa de valer para entrar na pelada.`}
          confirmLabel="Cancelar convite"
          pendingLabel="Cancelando…"
          cancelLabel="Manter convite"
          variant="destructive"
          loading={busy}
          onConfirm={cancel}
          onClose={() => setCanceling(null)}
        />
      )}
    </Card>
  )
}
