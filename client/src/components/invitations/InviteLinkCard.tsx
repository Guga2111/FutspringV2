import { useState } from "react"
import { format } from "date-fns"
import { HugeiconsIcon } from "@hugeicons/react"
import { Copy01Icon, Link01Icon, MoreHorizontalIcon, Share08Icon } from "@hugeicons/core-free-icons"
import { useInviteLink } from "@/components/invitations/hooks/useInviteLink"
import { formatInvitationDate, relativeToNow } from "@/utils/invitations"
import { ConfirmActionDialog } from "@/components/ConfirmActionDialog"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"

const confirmCopy = {
  replace: {
    title: "Gerar um novo link?",
    description: "O link atual para de funcionar. Quem ainda não entrou vai precisar do novo.",
    confirmLabel: "Gerar novo link",
  },
  revoke: {
    title: "Revogar o link?",
    description: "O link atual para de funcionar e a pelada fica sem link de convite.",
    confirmLabel: "Revogar link",
  },
} as const

export function InviteLinkCard({ peladaId, peladaName }: { peladaId: number; peladaName: string }) {
  const invite = useInviteLink(peladaId)
  const [confirm, setConfirm] = useState<"replace" | "revoke" | null>(null)
  const { link, url } = invite
  const busy = invite.pending !== null

  async function confirmAction() {
    if (confirm && (await invite.run(confirm))) setConfirm(null)
  }

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-3 space-y-0 p-4 md:p-6">
        <div className="flex min-w-0 flex-col gap-1">
          <CardTitle className="text-title font-semibold">Link de convite</CardTitle>
          <CardDescription>Quem tiver o link pode entrar na pelada. Cada link vale 7 dias.</CardDescription>
        </div>
        {!invite.loading && !invite.error && (
          <Badge
            variant="status"
            className={link ? "bg-success-muted text-success-soft" : "bg-secondary text-secondary-foreground"}
          >
            {link ? "Ativo" : "Sem link"}
          </Badge>
        )}
      </CardHeader>

      <CardContent className="flex flex-col gap-3 p-4 pt-0 md:p-6 md:pt-0">
        {invite.loading ? (
          <div className="flex flex-col gap-3" aria-hidden>
            <Skeleton className="h-10 rounded-md" />
            <Skeleton className="h-4 w-2/3" />
          </div>
        ) : invite.error ? (
          <Alert variant="destructive">
            <AlertDescription>
              Não foi possível carregar o link.{" "}
              <Button variant="link" className="h-auto p-0" onClick={invite.retry}>Tentar novamente</Button>
            </AlertDescription>
          </Alert>
        ) : link && url ? (
          <>
            <Label htmlFor="pelada-invite-link" className="sr-only">Link de convite</Label>
            <div className="flex flex-col gap-2 md:flex-row">
              <Input
                id="pelada-invite-link"
                readOnly
                value={url}
                onFocus={(e) => e.target.select()}
                className="min-w-0 flex-1 truncate bg-card-elevated text-body"
              />
              <div className="flex items-center gap-2">
                <Button variant="brand" disabled={busy} onClick={invite.copy} className="flex-1 md:flex-none">
                  <HugeiconsIcon icon={Copy01Icon} />
                  Copiar link
                </Button>
                {invite.canShare && (
                  <Button variant="outline" size="icon" disabled={busy} onClick={() => invite.share(peladaName)} aria-label="Compartilhar link">
                    <HugeiconsIcon icon={Share08Icon} />
                  </Button>
                )}
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="outline" size="icon" disabled={busy} aria-label="Mais ações do link">
                      <HugeiconsIcon icon={MoreHorizontalIcon} />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem onSelect={() => setConfirm("replace")}>Gerar novo link</DropdownMenuItem>
                    <DropdownMenuItem
                      className="text-destructive-soft focus:text-destructive-soft"
                      onSelect={() => setConfirm("revoke")}
                    >
                      Revogar link
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
            </div>
            <p className="text-body-sm text-muted-foreground">
              <span title={formatInvitationDate(link.expiresAt)}>Expira {relativeToNow(link.expiresAt, invite.now)}</span>
              {" · "}criado por {link.creator.username} em {format(new Date(link.createdAt), "dd/MM")}
            </p>
          </>
        ) : (
          <div className="flex flex-col items-start gap-3 rounded-tile bg-card-elevated p-4 md:flex-row md:items-center md:justify-between">
            <div className="flex items-center gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-background text-muted-foreground">
                <HugeiconsIcon icon={Link01Icon} className="size-5" />
              </span>
              <p className="text-body text-muted-foreground">Nenhum link ativo. Crie um para mandar no grupo da pelada.</p>
            </div>
            <Button variant="brand" disabled={busy} onClick={() => invite.run("create")} className="w-full md:w-auto">
              {invite.pending === "create" ? "Criando…" : "Criar link"}
            </Button>
          </div>
        )}
      </CardContent>

      {confirm && (
        <ConfirmActionDialog
          {...confirmCopy[confirm]}
          variant={confirm === "revoke" ? "destructive" : "default"}
          pendingLabel="Aguarde…"
          loading={busy}
          onConfirm={confirmAction}
          onClose={() => setConfirm(null)}
        />
      )}
    </Card>
  )
}
