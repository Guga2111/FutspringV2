import { useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Cancel01Icon, Notification01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { usePushSubscription } from "@/hooks/usePushSubscription"
import { usePwaInstall } from "@/hooks/usePwaInstall"
import { PUSH_CARD_DISMISSED_KEY, installCardVisible } from "@/utils/pwaPlatform"

function readDismissed(): string | null {
  try {
    return localStorage.getItem(PUSH_CARD_DISMISSED_KEY)
  } catch {
    return null
  }
}

// Home card in the installed app while push can still be turned on here. The button asks for the permission
// (a click, as iOS requires); the ✕ hides it for 30 days, like InstallAppCard.
export function EnablePushCard() {
  const { context } = usePwaInstall()
  const { status, pending, enable } = usePushSubscription()
  const [visible, setVisible] = useState(() => installCardVisible(readDismissed(), new Date()))

  if (!visible || context !== "installed" || status !== "inactive") return null

  function dismiss() {
    setVisible(false)
    try {
      localStorage.setItem(PUSH_CARD_DISMISSED_KEY, new Date().toISOString())
    } catch {
      // storage unavailable: the card just comes back next visit
    }
  }

  return (
    <section aria-label="Ativar notificações" className="flex items-start gap-3 rounded-tile border border-border bg-card p-4">
      <span className="flex size-10 shrink-0 items-center justify-center rounded-tile bg-brand-muted">
        <HugeiconsIcon icon={Notification01Icon} aria-hidden className="size-5 text-brand-text" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-3">
        <div className="flex flex-col gap-0.5">
          <h2 className="text-body-lg font-semibold">Ative as notificações</h2>
          <p className="text-body-sm text-muted-foreground">Saiba na hora quando os times saírem, a vaga abrir ou a sessão for cancelada.</p>
        </div>
        <Button variant="brand" size="sm" className="self-start" disabled={pending} onClick={() => void enable()}>
          Ativar notificações
        </Button>
      </div>
      <Button variant="ghost" size="icon" aria-label="Dispensar" className="-mr-2 -mt-2 shrink-0" onClick={dismiss}>
        <HugeiconsIcon icon={Cancel01Icon} className="size-4" />
      </Button>
    </section>
  )
}
