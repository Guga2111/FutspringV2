import { useState } from "react"
import { Link } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { Cancel01Icon, SmartPhone01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { usePwaInstall } from "@/hooks/usePwaInstall"
import { INSTALL_CARD_DISMISSED_KEY, installCardVisible } from "@/utils/pwaPlatform"

function readDismissed(): string | null {
  try {
    return localStorage.getItem(INSTALL_CARD_DISMISSED_KEY)
  } catch {
    return null
  }
}

// Home card on a phone's browser: put the app on the home screen. The ✕ hides it for 30 days.
export function InstallAppCard() {
  const { context, promptInstall } = usePwaInstall()
  const [visible, setVisible] = useState(() => installCardVisible(readDismissed(), new Date()))

  if (!visible || context === "installed" || context === "desktop") return null

  function dismiss() {
    setVisible(false)
    try {
      localStorage.setItem(INSTALL_CARD_DISMISSED_KEY, new Date().toISOString())
    } catch {
      // storage unavailable: the card just comes back next visit
    }
  }

  return (
    <section aria-label="Instalar o app" className="flex items-start gap-3 rounded-tile border border-border bg-card p-4">
      <span className="flex size-10 shrink-0 items-center justify-center rounded-tile bg-brand-muted">
        <HugeiconsIcon icon={SmartPhone01Icon} aria-hidden className="size-5 text-brand-text" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-3">
        <div className="flex flex-col gap-0.5">
          <h2 className="text-body-lg font-semibold">Leve o Futspring na tela de início</h2>
          <p className="text-body-sm text-muted-foreground">Abra como um app, direto do ícone, sem passar por loja.</p>
        </div>
        {context === "android-prompt" ? (
          <Button variant="brand" size="sm" className="self-start" onClick={() => void promptInstall()}>
            Instalar app
          </Button>
        ) : (
          <Button asChild variant="brand" size="sm" className="self-start">
            <Link to="/instalar">Ver como instalar</Link>
          </Button>
        )}
      </div>
      <Button variant="ghost" size="icon" aria-label="Dispensar" className="-mr-2 -mt-2 shrink-0" onClick={dismiss}>
        <HugeiconsIcon icon={Cancel01Icon} className="size-4" />
      </Button>
    </section>
  )
}
