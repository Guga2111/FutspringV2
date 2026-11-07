import { useState } from "react"
import { SmartPhone01Icon } from "@hugeicons/core-free-icons"
import { AuthHeader } from "@/components/auth/AuthHeader"
import { AuthLayout } from "@/components/auth/AuthLayout"
import { AlreadyInstalled, AndroidSteps, DesktopInstall, InAppNotice, IosSteps } from "@/components/install/InstallSteps"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { usePwaInstall } from "@/hooks/usePwaInstall"
import { installPlatform, type InstallPlatform } from "@/utils/pwaPlatform"

const PLATFORM_LABEL: Record<InstallPlatform, string> = { ios: "iPhone", android: "Android" }

// Public tutorial to put the app on the home screen, for the platform detected (the /auth QR Code,
// the Home card and the settings link here). The chips at the bottom cover a wrong detection.
export default function InstallPage() {
  const { context, promptInstall } = usePwaInstall()
  const [chosen, setChosen] = useState<InstallPlatform | null>(null)
  const detected = installPlatform(context, navigator.userAgent)
  const platform = chosen ?? detected
  const url = `${window.location.origin}/instalar`

  async function install() {
    await promptInstall()
  }

  return (
    <AuthLayout>
      <div className="flex w-full max-w-md flex-col gap-6">
        <AuthHeader icon={SmartPhone01Icon} title="Instale o Futspring">
          Na tela de início ele abre como um app, sem passar por loja.
        </AuthHeader>

        {context === "installed" ? (
          <AlreadyInstalled />
        ) : (
          <>
            {context === "in-app" && !chosen && <InAppNotice url={url} />}
            {platform === "ios" && <IosSteps otherBrowser={context === "ios-other" && !chosen} />}
            {platform === "android" && <AndroidSteps onInstall={context === "android-prompt" && !chosen ? install : undefined} />}
            {platform === null && <DesktopInstall url={url} />}

            <div className="flex flex-wrap items-center justify-center gap-2">
              <span className="text-body-sm text-muted-foreground">Mostrar passos do</span>
              <ToggleGroup
                type="single"
                variant="chip"
                size="chip"
                value={platform ?? ""}
                onValueChange={(value) => value && setChosen(value as InstallPlatform)}
                aria-label="Mostrar passos do aparelho"
                className="gap-1.5"
              >
                {(Object.keys(PLATFORM_LABEL) as InstallPlatform[]).map((value) => (
                  <ToggleGroupItem key={value} value={value}>
                    {PLATFORM_LABEL[value]}
                  </ToggleGroupItem>
                ))}
              </ToggleGroup>
            </div>
          </>
        )}
      </div>
    </AuthLayout>
  )
}
