// Where the app is open, to show the right install steps (/instalar, the Home card)
export type InstallContext =
  | "installed" // opened from the home screen icon
  | "ios-safari"
  | "ios-other" // Chrome / Firefox / Edge on iOS: same steps, the share button sits in the address bar
  | "in-app" // WhatsApp / Instagram / Facebook web view: open in the browser first
  | "android-prompt" // the browser offered beforeinstallprompt: one button installs
  | "android-manual"
  | "desktop"

export interface InstallEnvironment {
  userAgent: string
  maxTouchPoints: number
  standalone: boolean
  hasInstallPrompt: boolean
}

const IN_APP = /FBAN|FBAV|Instagram|WhatsApp|; wv\)/
const IOS_DEVICE = /iPhone|iPad|iPod/
const IOS_OTHER_BROWSER = /CriOS|FxiOS|EdgiOS/

export function detectInstallContext({ userAgent, maxTouchPoints, standalone, hasInstallPrompt }: InstallEnvironment): InstallContext {
  if (standalone) return "installed"
  if (IN_APP.test(userAgent)) return "in-app"
  // iPadOS identifies itself as a Mac; a touch screen gives it away
  const ios = IOS_DEVICE.test(userAgent) || (/Macintosh/.test(userAgent) && maxTouchPoints > 1)
  if (ios) return IOS_OTHER_BROWSER.test(userAgent) ? "ios-other" : "ios-safari"
  // desktop Chrome sends the prompt too, but the install flow targets phones (the desktop gets a QR Code)
  if (/Android/.test(userAgent)) return hasInstallPrompt ? "android-prompt" : "android-manual"
  return "desktop"
}

export type InstallPlatform = "ios" | "android"

// The steps shown for a context (in-app shows the steps of the phone underneath)
export function installPlatform(context: InstallContext, userAgent: string): InstallPlatform | null {
  switch (context) {
    case "ios-safari":
    case "ios-other":
      return "ios"
    case "android-prompt":
    case "android-manual":
      return "android"
    case "in-app":
      return /Android/.test(userAgent) ? "android" : "ios"
    default:
      return null
  }
}

// The Home card's ✕ stores the day it was dismissed; the card comes back 30 days later
export const INSTALL_CARD_DISMISSED_KEY = "install_card_dismissed"
const INSTALL_CARD_PAUSE_MS = 30 * 24 * 60 * 60 * 1000

export function installCardVisible(dismissedAt: string | null, now: Date): boolean {
  if (!dismissedAt) return true
  const time = Date.parse(dismissedAt)
  if (Number.isNaN(time)) return true
  return now.getTime() - time >= INSTALL_CARD_PAUSE_MS
}
