import { useSyncExternalStore } from "react"
import { getInstallPrompt, showInstallPrompt, subscribeInstallPrompt, wasJustInstalled } from "@/lib/installPrompt"
import { detectInstallContext, type InstallContext } from "@/utils/pwaPlatform"

const STANDALONE_QUERY = "(display-mode: standalone)"

function subscribe(onChange: () => void) {
  const mql = window.matchMedia(STANDALONE_QUERY)
  mql.addEventListener("change", onChange)
  const unsubscribe = subscribeInstallPrompt(onChange)
  return () => {
    mql.removeEventListener("change", onChange)
    unsubscribe()
  }
}

function readContext(): InstallContext {
  // navigator.standalone: iOS Safari's own flag for a home screen app
  const standalone =
    window.matchMedia(STANDALONE_QUERY).matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true ||
    wasJustInstalled()
  return detectInstallContext({
    userAgent: navigator.userAgent,
    maxTouchPoints: navigator.maxTouchPoints,
    standalone,
    hasInstallPrompt: getInstallPrompt() !== null,
  })
}

// Where the app is open (installed, iPhone, Android, in-app browser, desktop) and the one-tap install on Android
export function usePwaInstall(): { context: InstallContext; promptInstall: () => Promise<boolean> } {
  const context = useSyncExternalStore(subscribe, readContext, () => "desktop" as const)
  return { context, promptInstall: showInstallPrompt }
}
