import { Link } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { Notification01Icon, NotificationBlock01Icon, NotificationOff01Icon, SmartPhone01Icon } from "@hugeicons/core-free-icons"
import type { IconSvgElement } from "@hugeicons/react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import type { PushSubscriptionState } from "@/hooks/usePushSubscription"
import type { PushStatus } from "@/types/push"

type PlainStatus = Exclude<PushStatus, "denied">

const STATUS_VIEW: Record<PlainStatus, { icon: IconSvgElement; text: string }> = {
  active: { icon: Notification01Icon, text: "Notificações ativas neste aparelho" },
  inactive: { icon: Notification01Icon, text: "Receba avisos de sessão, vaga e times no celular" },
  needsInstall: { icon: SmartPhone01Icon, text: "No iPhone, as notificações só chegam no app instalado" },
  unconfigured: { icon: NotificationOff01Icon, text: "Este navegador não recebe notificações" },
  unsupported: { icon: NotificationOff01Icon, text: "Este navegador não recebe notificações" },
}

// "Neste aparelho": Web Push on this device (usePushSubscription), above the per-category switches
export function DevicePushBlock({ push }: { push: PushSubscriptionState }) {
  const { status, pending, enable, disable } = push

  if (status === null) return <Skeleton className="h-[52px] w-full rounded-tile" />

  if (status === "denied") {
    return (
      <Alert>
        <HugeiconsIcon icon={NotificationBlock01Icon} className="size-4" />
        <AlertTitle>As notificações estão bloqueadas</AlertTitle>
        <AlertDescription className="flex flex-col gap-1 text-muted-foreground">
          <span>No iPhone: Ajustes → Notificações → Futspring → Permitir Notificações.</span>
          <span>No Android: toque no cadeado (ou ⓘ) ao lado do endereço → Permissões → Notificações.</span>
        </AlertDescription>
      </Alert>
    )
  }

  const { icon, text } = STATUS_VIEW[status]
  return (
    <div className="flex flex-col gap-3 rounded-tile border bg-card-elevated p-3 md:flex-row md:items-center">
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-brand-muted">
          <HugeiconsIcon icon={icon} aria-hidden className="size-[18px] text-brand-text" />
        </span>
        <p className="text-body font-medium">{text}</p>
      </div>
      {status === "active" && (
        <Button variant="outline" size="sm" className="self-start md:self-auto" disabled={pending} onClick={() => void disable()}>
          Desativar neste aparelho
        </Button>
      )}
      {status === "inactive" && (
        <Button variant="brand" size="sm" className="self-start md:self-auto" disabled={pending} onClick={() => void enable()}>
          Ativar notificações
        </Button>
      )}
      {status === "needsInstall" && (
        <Button asChild variant="outline" size="sm" className="self-start md:self-auto">
          <Link to="/instalar">Como instalar</Link>
        </Button>
      )}
    </div>
  )
}
