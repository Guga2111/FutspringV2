import { useState } from "react"
import { Link } from "react-router-dom"
import { HugeiconsIcon, type IconSvgElement } from "@hugeicons/react"
import { AppleIcon, Copy01Icon, Download04Icon, PlayStoreIcon, Tick02Icon } from "@hugeicons/core-free-icons"
import { QRCodeSVG } from "qrcode.react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { MOBILE_APPS, MOBILE_PLATFORMS, type MobilePlatform } from "@/lib/mobileApps"

const PLATFORM_LABEL: Record<MobilePlatform, string> = { ios: "iOS", android: "Android" }
const PLATFORM_ICON: Record<MobilePlatform, IconSvgElement> = { ios: AppleIcon, android: PlayStoreIcon }

// Desktop side panel of the auth page, scanned with the phone's camera: the store's QR Code once the
// app is published, until then the QR Code of /instalar (the browser install, the same for both platforms)
export function AppDownloadPanel() {
  const [platform, setPlatform] = useState<MobilePlatform>("ios")
  const [copied, setCopied] = useState(false)
  const app = MOBILE_APPS[platform]
  const url = app.url ?? `${window.location.origin}/instalar`

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error("Não foi possível copiar. Selecione o link e copie manualmente.")
    }
  }

  return (
    <section aria-labelledby="app-download-title" className="flex w-full max-w-sm flex-col items-center gap-6 text-center">
      <header className="flex flex-col gap-2">
        <h2 id="app-download-title" className="font-display text-heading-lg font-bold leading-none">
          Leve o Futspring no bolso
        </h2>
        <p className="text-body-lg text-muted-foreground">
          Aponte a câmera do celular, instale o app e entre com a mesma conta.
        </p>
      </header>

      <ToggleGroup
        type="single"
        value={platform}
        onValueChange={(value) => value && setPlatform(value as MobilePlatform)}
        aria-label="Sistema do celular"
        className="rounded-full bg-muted p-1"
      >
        {MOBILE_PLATFORMS.map((value) => (
          <ToggleGroupItem
            key={value}
            value={value}
            className="h-9 rounded-full px-5 text-body data-[state=on]:bg-background data-[state=on]:text-foreground"
          >
            <HugeiconsIcon icon={PLATFORM_ICON[value]} className="size-4" />
            {PLATFORM_LABEL[value]}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>

      <div className="flex flex-col items-center gap-3">
        {/* QR Codes need a light quiet zone to scan, so the frame is white in both themes */}
        <div className="rounded-hero bg-paper p-4 text-night">
          <QRCodeSVG
            value={url}
            size={168}
            fgColor="currentColor"
            bgColor="transparent"
            title={app.url ? `QR Code do app na ${app.store}` : "QR Code da página de instalação"}
          />
        </div>
        <p className="text-caption text-muted-foreground">{app.url ? app.device : "Instale pelo navegador, sem loja"}</p>
      </div>

      <div className="flex w-full items-center gap-2">
        <Input
          readOnly
          value={url}
          aria-label={app.url ? `Link do app na ${app.store}` : "Link da página de instalação"}
          className="h-10 flex-1"
          onFocus={(e) => e.currentTarget.select()}
        />
        <Button type="button" variant="outline" size="sm" onClick={copyLink}>
          <HugeiconsIcon icon={copied ? Tick02Icon : Copy01Icon} className="size-4" />
          {copied ? "Copiado" : "Copiar link"}
        </Button>
      </div>

      {!app.url && (
        <p className="text-body-sm text-muted-foreground">
          {app.store} · Em breve
        </p>
      )}
    </section>
  )
}

// Below the form on phones and tablets: the browser install and straight links to the stores
// (a QR Code makes no sense on the phone itself)
export function AppDownloadLinks() {
  return (
    <section aria-label="App para celular" className="flex flex-col items-center gap-3 lg:hidden">
      <p className="text-body-sm text-muted-foreground">Também no celular</p>
      <div className="flex flex-wrap justify-center gap-2">
        <Button asChild variant="outline" size="sm">
          <Link to="/instalar">
            <HugeiconsIcon icon={Download04Icon} className="size-4" />
            Instalar pelo navegador
          </Link>
        </Button>
        {MOBILE_PLATFORMS.map((platform) => {
          const app = MOBILE_APPS[platform]
          const content = (
            <>
              <HugeiconsIcon icon={PLATFORM_ICON[platform]} className="size-4" />
              {app.store}
              {!app.url && <span className="text-muted-foreground">· Em breve</span>}
            </>
          )
          return app.url ? (
            <Button key={platform} asChild variant="outline" size="sm">
              <a href={app.url} target="_blank" rel="noopener noreferrer">{content}</a>
            </Button>
          ) : (
            <Button key={platform} variant="outline" size="sm" disabled>{content}</Button>
          )
        })}
      </div>
    </section>
  )
}
