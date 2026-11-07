// Chrome's install prompt (Android, desktop). It can fire before any page mounts, so main.tsx
// captures it at startup and usePwaInstall reads it from here.
export interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>
}

let deferred: BeforeInstallPromptEvent | null = null
let installed = false
const listeners = new Set<() => void>()

function emit() {
  for (const listener of listeners) listener()
}

export function captureInstallPrompt() {
  window.addEventListener("beforeinstallprompt", (event) => {
    // keep it for our "Instalar app" button instead of the browser's mini bar
    event.preventDefault()
    deferred = event as BeforeInstallPromptEvent
    emit()
  })
  window.addEventListener("appinstalled", () => {
    deferred = null
    installed = true
    emit()
  })
}

export function subscribeInstallPrompt(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function getInstallPrompt(): BeforeInstallPromptEvent | null {
  return deferred
}

export function wasJustInstalled(): boolean {
  return installed
}

// The prompt can only be shown once
export async function showInstallPrompt(): Promise<boolean> {
  const event = deferred
  if (!event) return false
  deferred = null
  emit()
  await event.prompt()
  const { outcome } = await event.userChoice
  return outcome === "accepted"
}
