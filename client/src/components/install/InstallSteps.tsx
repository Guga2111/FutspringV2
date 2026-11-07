import { useState, type ReactNode } from "react"
import { Link } from "react-router-dom"
import { HugeiconsIcon, type IconSvgElement } from "@hugeicons/react"
import {
  Copy01Icon,
  Download04Icon,
  MoreHorizontalIcon,
  MoreVerticalIcon,
  PlusSignSquareIcon,
  Share03Icon,
  SmartPhone01Icon,
  Tick02Icon,
  ToggleOnIcon,
} from "@hugeicons/core-free-icons"
import { QRCodeSVG } from "qrcode.react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"

interface Step {
  id: string
  icon: IconSvgElement
  text: ReactNode
}

// A card with a numbered list of steps, each with the icon of the button the person will tap.
// Icons instead of screenshots: screenshots get old with every iOS version.
function StepsCard({ title, steps, children }: { title: string; steps: Step[]; children?: ReactNode }) {
  return (
    <section aria-label={title} className="flex flex-col gap-4 rounded-hero bg-card p-5">
      <h2 className="text-title font-semibold">{title}</h2>
      {children}
      <ol className="flex flex-col gap-4">
        {steps.map((step, index) => (
          <li key={step.id} className="flex items-start gap-3">
            <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-brand-muted text-body-sm font-semibold text-brand-text">
              {index + 1}
            </span>
            <p className="flex-1 pt-0.5 text-body text-foreground">{step.text}</p>
            <span className="flex size-9 shrink-0 items-center justify-center rounded-tile border border-border bg-background">
              <HugeiconsIcon icon={step.icon} aria-hidden className="size-5 text-muted-foreground" />
            </span>
          </li>
        ))}
      </ol>
    </section>
  )
}

const OPEN_FROM_ICON: Step = {
  id: "open-from-icon",
  icon: SmartPhone01Icon,
  text: (
    <>
      Abra o Futspring <strong>pelo ícone</strong> na tela de início. Só o app aberto pelo ícone recebe notificações.
    </>
  ),
}

// otherBrowser: Chrome / Firefox / Edge on iOS, whose share button sits in the address bar
export function IosSteps({ otherBrowser = false }: { otherBrowser?: boolean }) {
  const steps: Step[] = [
    {
      id: "ios-share",
      icon: Share03Icon,
      text: otherBrowser ? (
        <>
          Toque em <strong>Compartilhar</strong> (o quadrado com a seta), na barra de endereço.
        </>
      ) : (
        <>
          Toque em <strong>Compartilhar</strong> (o quadrado com a seta). No iOS 26, com a barra compacta, ele fica no menu{" "}
          <strong>•••</strong>.
        </>
      ),
    },
    {
      id: "ios-add",
      icon: PlusSignSquareIcon,
      text: (
        <>
          Role e toque em <strong>Adicionar à Tela de Início</strong> (se não aparecer, toque em “Ver mais”).
        </>
      ),
    },
    {
      id: "ios-web-app",
      icon: ToggleOnIcon,
      text: (
        <>
          Deixe <strong>Abrir como app Web</strong> ligado e toque em <strong>Adicionar</strong>.
        </>
      ),
    },
    OPEN_FROM_ICON,
  ]
  return <StepsCard title="No iPhone" steps={steps} />
}

// onInstall: Chrome offered its install prompt, so one button does it; the manual steps stay below
export function AndroidSteps({ onInstall }: { onInstall?: () => void }) {
  const steps: Step[] = [
    {
      id: "android-menu",
      icon: MoreVerticalIcon,
      text: (
        <>
          Toque no menu <strong>⋮</strong> do navegador.
        </>
      ),
    },
    {
      id: "android-install",
      icon: Download04Icon,
      text: (
        <>
          Toque em <strong>Instalar app</strong> (ou “Adicionar à tela inicial”).
        </>
      ),
    },
    {
      id: "android-confirm",
      icon: Tick02Icon,
      text: (
        <>
          Confirme em <strong>Instalar</strong>.
        </>
      ),
    },
    OPEN_FROM_ICON,
  ]
  return (
    <StepsCard title="No Android" steps={steps}>
      {onInstall && (
        <>
          <Button variant="brand" onClick={onInstall} className="self-start">
            <HugeiconsIcon icon={Download04Icon} className="size-4" />
            Instalar app
          </Button>
          <p className="text-body-sm text-muted-foreground">Se o botão não abrir nada, siga os passos:</p>
        </>
      )}
    </StepsCard>
  )
}

// WhatsApp / Instagram / Facebook open links in their own browser, which can't install apps
export function InAppNotice({ url }: { url: string }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error("Não foi possível copiar. Selecione o link e copie manualmente.")
    }
  }

  const steps: Step[] = [
    {
      id: "in-app-menu",
      icon: MoreHorizontalIcon,
      text: (
        <>
          Toque em <strong>•••</strong> (ou <strong>⋮</strong>) no canto da tela.
        </>
      ),
    },
    {
      id: "in-app-open",
      icon: Share03Icon,
      text: (
        <>
          Escolha <strong>Abrir no navegador</strong> (Safari ou Chrome) e siga os passos abaixo por lá.
        </>
      ),
    },
  ]
  return (
    <StepsCard title="Abra no navegador primeiro" steps={steps}>
      <p className="text-body text-muted-foreground">
        Este link abriu dentro de outro app, que não instala o Futspring. Se não achar a opção, copie o link e cole no navegador.
      </p>
      <Button variant="outline" onClick={copy} className="self-start">
        <HugeiconsIcon icon={copied ? Tick02Icon : Copy01Icon} className="size-4" />
        {copied ? "Copiado" : "Copiar link"}
      </Button>
    </StepsCard>
  )
}

export function DesktopInstall({ url }: { url: string }) {
  return (
    <section aria-label="Instalar pelo celular" className="flex flex-col items-center gap-4 rounded-hero bg-card p-6 text-center">
      {/* QR Codes need a light quiet zone to scan, so the frame is white in both themes */}
      <div className="rounded-hero bg-paper p-4 text-night">
        <QRCodeSVG value={url} size={168} fgColor="currentColor" bgColor="transparent" title="QR Code da página de instalação" />
      </div>
      <h2 className="text-title font-semibold">Abra este link no celular</h2>
      <p className="text-body text-muted-foreground">Aponte a câmera do celular para o QR Code e siga os passos do seu aparelho.</p>
    </section>
  )
}

export function AlreadyInstalled() {
  return (
    <section aria-label="App instalado" className="flex flex-col items-center gap-4 rounded-hero bg-card p-6 text-center">
      <h2 className="text-title font-semibold">Você já está no app</h2>
      <p className="text-body text-muted-foreground">O Futspring está instalado neste aparelho.</p>
      <Button asChild variant="brand">
        <Link to="/home">Ir para o início</Link>
      </Button>
    </section>
  )
}
